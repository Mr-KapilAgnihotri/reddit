package com.kapil.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.reddit.auth.repository.EmailVerificationTokenRepository;
import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.repository.PostRepository;
import com.kapil.reddit.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full backend side of the moderation pipeline, against real Postgres (pgvector),
 * Redis and an embedded Kafka broker. The Python ML service is simulated by publishing
 * the exact JSON it produces to {@code post-moderated} / {@code post-embedding}.
 *
 * <p>Covers the HANDOFF fixes end-to-end: the {@code isModerated} JSON name, the
 * dedicated embedding deserializer, reading {@code vector(384)} back through Hibernate,
 * the recommended-feed security matcher, the role authorities and the public health probe.
 */
class PostModerationPipelineIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password12!";

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender javaMailSender;

    /** Sends raw JSON strings, exactly like the Python kafka-python producer does. */
    private KafkaTemplate<String, String> rawProducer;

    @BeforeEach
    void setUpProducer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                props, new StringSerializer(), new StringSerializer()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    /** Register → verify (token read from the DB) → login. Returns the access token. */
    private String registerVerifyAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "p-" + suffix + "@test.com";

        ResponseEntity<String> reg = restTemplate.postForEntity(url("/api/users"),
                new HttpEntity<>(Map.of("username", "user-" + suffix, "email", email, "password", PASSWORD),
                        headers(null)), String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.OK);
        long userId = json(reg).get("id").asLong();

        String token = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId)
                .orElseThrow().getToken();
        assertThat(restTemplate.getForEntity(url("/api/auth/verify?token=" + token), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        return login(email, PASSWORD);
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> login = restTemplate.postForEntity(url("/api/auth/login"),
                new HttpEntity<>(Map.of("email", email, "password", password), headers(null)), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(login).get("access_token").asText();
    }

    private JsonNode createPost(String token, String title, String text) throws Exception {
        ResponseEntity<String> res = restTemplate.exchange(url("/api/posts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("title", title, "text", text), headers(token)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(res);
    }

    private JsonNode getPost(long id) throws Exception {
        ResponseEntity<String> res = restTemplate.getForEntity(url("/api/posts/" + id), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(res);
    }

    /** Polls GET /api/posts/{id} until the predicate holds (max ~15 s). */
    private JsonNode awaitPost(long id, Predicate<JsonNode> condition) throws Exception {
        JsonNode last = null;
        for (int i = 0; i < 60; i++) {
            last = getPost(id);
            if (condition.test(last)) return last;
            Thread.sleep(250);
        }
        return last;
    }

    /** Polls the DB until the post has an embedding stored (max ~15 s). */
    private Post awaitEmbedding(long id) throws Exception {
        for (int i = 0; i < 60; i++) {
            Post p = postRepository.findById(id).orElseThrow();
            if (p.getEmbedding() != null) return p;
            Thread.sleep(250);
        }
        return postRepository.findById(id).orElseThrow();
    }

    private void publishEmbedding(long postId, float[] vector) throws Exception {
        List<Float> values = new ArrayList<>();
        for (float v : vector) values.add(v);
        rawProducer.send("post-embedding",
                objectMapper.writeValueAsString(Map.of("postId", postId, "embedding", values))).get();
    }

    /** Deterministic unit vector; small {@code tilt} values give vectors close to each other. */
    private static float[] vector(float tilt) {
        float[] v = new float[384];
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (1.0 + tilt * Math.sin(i));
            norm += v[i] * v[i];
        }
        for (int i = 0; i < v.length; i++) v[i] /= (float) Math.sqrt(norm);
        return v;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void createPost_publishesPostCreatedEventWithTitle_afterCommit() throws Exception {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "pipeline-it-" + System.nanoTime(), "false", embeddedKafkaBroker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            TopicPartition tp = new TopicPartition("post-created", 0);
            consumer.assign(List.of(tp));
            consumer.seekToEnd(List.of(tp));
            consumer.position(tp);

            String token = registerVerifyAndLogin();
            long postId = createPost(token, "kafka title", "kafka body").get("id").asLong();

            JsonNode event = null;
            long deadline = System.currentTimeMillis() + 10_000;
            while (event == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode node = objectMapper.readTree(rec.value());
                    if (node.path("postId").asLong() == postId) event = node;
                }
            }

            assertThat(event).as("post-created event for post %d", postId).isNotNull();
            assertThat(event.get("text").asText()).isEqualTo("kafka body");
            assertThat(event.get("title").asText()).isEqualTo("kafka title");
            assertThat(event.get("type").asText()).isEqualTo("POST");
        }
    }

    @Test
    void profanePost_isMaskedAndFlagged_whenMlResultArrives() throws Exception {
        String token = registerVerifyAndLogin();
        JsonNode created = createPost(token, "damn title", "what the fuck is this");
        long postId = created.get("id").asLong();

        // Immediately after creation the post is still unmoderated (async pipeline).
        assertThat(created.get("moderated").asBoolean()).isFalse();
        // Prime the Redis postDetail cache so we also prove it gets evicted.
        assertThat(getPost(postId).get("text").asText()).isEqualTo("what the fuck is this");

        // Exactly the payload shape ml-service/app/consumer.py publishes.
        String mlResult = """
                {"postId": %d, "maskedText": "what the **** is this", "maskedTitle": "**** title",
                 "isModerated": true, "categories": ["profanity"], "severity": 0.6,
                 "reason": "profanity filter", "provider": "better_profanity"}
                """.formatted(postId);
        rawProducer.send("post-moderated", mlResult).get();

        JsonNode post = awaitPost(postId, p -> p.get("moderated").asBoolean());

        assertThat(post.get("moderated").asBoolean()).isTrue();
        assertThat(post.get("text").asText()).isEqualTo("what the **** is this");
        assertThat(post.get("title").asText()).isEqualTo("**** title");

        Post entity = postRepository.findById(postId).orElseThrow();
        assertThat(entity.getIsModerated()).isTrue();
        // The original text is kept for admins / audits.
        assertThat(entity.getOriginalText()).isEqualTo("what the fuck is this");
    }

    /**
     * Regression: GET /api/posts/{id} used to load the entity, bump viewCount and save the
     * whole row. A read racing the moderation commit flushed the stale display_text /
     * is_moderated=false back, and could also cache it in Redis for the full TTL.
     */
    @Test
    void concurrentReads_doNotRevertOrStaleCacheModeration() throws Exception {
        String token = registerVerifyAndLogin();
        long postId = createPost(token, "race", "fuck this race").get("id").asLong();

        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.ExecutorService readers = java.util.concurrent.Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            readers.submit(() -> {
                while (!stop.get()) {
                    restTemplate.getForEntity(url("/api/posts/" + postId), String.class);
                    // What the UI polls after posting: the author's own (per-user cached) feed.
                    restTemplate.exchange(url("/api/posts/global?sort=new"), HttpMethod.GET,
                            new HttpEntity<>(headers(token)), String.class);
                }
                return null;
            });
        }
        try {
            Thread.sleep(300);
            rawProducer.send("post-moderated", """
                    {"postId": %d, "maskedText": "**** this race", "isModerated": true}
                    """.formatted(postId)).get();
            Thread.sleep(3000);
        } finally {
            stop.set(true);
            readers.shutdown();
            readers.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }

        Post entity = postRepository.findById(postId).orElseThrow();
        assertThat(entity.getIsModerated()).isTrue();
        assertThat(entity.getDisplayText()).isEqualTo("**** this race");
        assertThat(entity.getViewCount()).isGreaterThan(0L);

        JsonNode post = getPost(postId);
        assertThat(post.get("moderated").asBoolean()).isTrue();
        assertThat(post.get("text").asText()).isEqualTo("**** this race");

        JsonNode feed = json(restTemplate.exchange(url("/api/posts/global?sort=new"), HttpMethod.GET,
                new HttpEntity<>(headers(token)), String.class));
        JsonNode inFeed = null;
        for (JsonNode n : feed.get("content")) if (n.get("id").asLong() == postId) inFeed = n;
        assertThat(inFeed).as("post %d in author's global feed", postId).isNotNull();
        assertThat(inFeed.get("moderated").asBoolean()).isTrue();
        assertThat(inFeed.get("text").asText()).isEqualTo("**** this race");
    }

    @Test
    void cleanPost_staysUnmoderated() throws Exception {
        String token = registerVerifyAndLogin();
        long postId = createPost(token, "nice", "lovely weather today").get("id").asLong();

        rawProducer.send("post-moderated", """
                {"postId": %d, "maskedText": "lovely weather today", "isModerated": false}
                """.formatted(postId)).get();

        // Wait until the consumer has touched the row (updatedAt changes), then check.
        Thread.sleep(2000);
        JsonNode post = getPost(postId);
        assertThat(post.get("moderated").asBoolean()).isFalse();
        assertThat(post.get("text").asText()).isEqualTo("lovely weather today");
    }

    @Test
    void embedding_isStored_andReadBackThroughFeeds() throws Exception {
        String token = registerVerifyAndLogin();
        long postId = createPost(token, "vec", "embedding post").get("id").asLong();

        float[] vec = vector(0.1f);
        publishEmbedding(postId, vec);

        Post stored = awaitEmbedding(postId);
        assertThat(stored.getEmbedding()).as("embedding stored").isNotNull().hasSize(384);
        assertThat(stored.getEmbedding()[0]).isCloseTo(vec[0], org.assertj.core.data.Offset.offset(1e-6f));

        // Feeds load posts that now carry a vector column — must not blow up on read.
        assertThat(restTemplate.getForEntity(url("/api/posts/global?sort=new"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getPost(postId).get("id").asLong()).isEqualTo(postId);
    }

    @Test
    void malformedMessages_doNotBreakConsumers() throws Exception {
        rawProducer.send("post-moderated", "NOT JSON").get();
        rawProducer.send("post-embedding", "{broken").get();

        // A valid message afterwards must still be processed.
        String token = registerVerifyAndLogin();
        long postId = createPost(token, "after", "shit happens").get("id").asLong();
        rawProducer.send("post-moderated", """
                {"postId": %d, "maskedText": "**** happens", "isModerated": true}
                """.formatted(postId)).get();

        JsonNode post = awaitPost(postId, p -> p.get("moderated").asBoolean());
        assertThat(post.get("moderated").asBoolean()).isTrue();
        assertThat(post.get("text").asText()).isEqualTo("**** happens");
    }

    @Test
    void recommendedFeed_requiresAuth_andUsesVectorSimilarity() throws Exception {
        assertThat(restTemplate.getForEntity(url("/api/posts/recommended"), String.class)
                .getStatusCode().is4xxClientError()).isTrue();

        String author = registerVerifyAndLogin();
        long liked = createPost(author, "hiking", "mountain hiking trail").get("id").asLong();
        long similar = createPost(author, "trekking", "alpine trekking route").get("id").asLong();
        publishEmbedding(liked, vector(0.10f));
        publishEmbedding(similar, vector(0.12f));
        awaitEmbedding(liked);
        awaitEmbedding(similar);

        String reader = registerVerifyAndLogin();
        ResponseEntity<String> vote = restTemplate.exchange(url("/api/posts/" + liked + "/vote"),
                HttpMethod.POST, new HttpEntity<>(Map.of("value", 1), headers(reader)), String.class);
        assertThat(vote.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> rec = restTemplate.exchange(url("/api/posts/recommended?page=0&size=50"),
                HttpMethod.GET, new HttpEntity<>(headers(reader)), String.class);
        assertThat(rec.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Long> ids = new ArrayList<>();
        json(rec).get("content").forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).contains(similar).doesNotContain(liked);
    }

    @Test
    void healthEndpoint_isPublic() throws Exception {
        ResponseEntity<String> res = restTemplate.getForEntity(url("/api/health"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(res).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void staticFrontend_isServedWithoutLogin() {
        ResponseEntity<String> res = restTemplate.getForEntity(url("/"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsIgnoringCase("<html");
    }

    @Test
    void roleAuthorities_areGranted_fromJwt() throws Exception {
        String userToken = registerVerifyAndLogin();
        String adminToken = login("admin@example.com", "Password123!");

        ResponseEntity<String> userPing = restTemplate.exchange(url("/api/users/user/ping"),
                HttpMethod.GET, new HttpEntity<>(headers(userToken)), String.class);
        ResponseEntity<String> userOnAdmin = restTemplate.exchange(url("/api/users/admin/ping"),
                HttpMethod.GET, new HttpEntity<>(headers(userToken)), String.class);
        ResponseEntity<String> adminPing = restTemplate.exchange(url("/api/users/admin/ping"),
                HttpMethod.GET, new HttpEntity<>(headers(adminToken)), String.class);

        assertThat(userPing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userOnAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(adminPing.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanOverrideModeration() throws Exception {
        String userToken = registerVerifyAndLogin();
        String adminToken = login("admin@example.com", "Password123!");
        long postId = createPost(userToken, "t", "borderline text").get("id").asLong();

        ResponseEntity<String> forbidden = restTemplate.exchange(url("/api/admin/posts/" + postId + "/moderation"),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("isModerated", true), headers(userToken)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> ok = restTemplate.exchange(url("/api/admin/posts/" + postId + "/moderation"),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("isModerated", true, "displayText", "[removed]"),
                        headers(adminToken)), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Post entity = postRepository.findById(postId).orElseThrow();
        assertThat(entity.getIsModerated()).isTrue();
        assertThat(entity.getDisplayText()).isEqualTo("[removed]");
    }
}
