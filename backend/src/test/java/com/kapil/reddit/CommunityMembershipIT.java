package com.kapil.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapil.reddit.auth.repository.EmailVerificationTokenRepository;
import com.kapil.reddit.support.AbstractIntegrationTest;
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
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Join / leave / "my communities" flow used by the Communities page of the UI.
 */
class CommunityMembershipIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

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

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers(token)), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private String registerVerifyAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "c-" + suffix + "@test.com";
        ResponseEntity<String> reg = call(HttpMethod.POST, "/api/users", null,
                Map.of("username", "cuser-" + suffix, "email", email, "password", "Password12!"));
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.OK);
        long userId = json(reg).get("id").asLong();
        String token = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId).orElseThrow().getToken();
        restTemplate.getForEntity(url("/api/auth/verify?token=" + token), String.class);
        ResponseEntity<String> login = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", email, "password", "Password12!"));
        return json(login).get("access_token").asText();
    }

    private long createCommunity(String token) throws Exception {
        String name = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        ResponseEntity<String> res = call(HttpMethod.POST, "/api/communities", token,
                Map.of("name", name, "description", "test", "isPrivate", false));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(res).get("id").asLong();
    }

    private List<Long> ids(ResponseEntity<String> res) throws Exception {
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Long> ids = new ArrayList<>();
        JsonNode node = json(res);
        (node.has("content") ? node.get("content") : node).forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private List<Long> joined(String token) throws Exception {
        return ids(call(HttpMethod.GET, "/api/users/me/communities/joined", token, null));
    }

    private long memberCount(String token, long communityId) throws Exception {
        for (JsonNode c : json(call(HttpMethod.GET, "/api/communities", token, null))) {
            if (c.get("id").asLong() == communityId) return c.get("memberCount").asLong();
        }
        throw new AssertionError("community " + communityId + " not listed");
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void creator_isMemberAndOwner_andCannotLeave() throws Exception {
        String owner = registerVerifyAndLogin();
        long id = createCommunity(owner);

        assertThat(joined(owner)).contains(id);
        assertThat(ids(call(HttpMethod.GET, "/api/users/me/communities/created", owner, null))).contains(id);

        ResponseEntity<String> leave = call(HttpMethod.POST, "/api/communities/" + id + "/leave", owner, null);
        assertThat(leave.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(leave.getBody()).contains("Owner cannot leave");
        assertThat(joined(owner)).contains(id);
    }

    @Test
    void join_thenLeave_updatesJoinedListAndMemberCount() throws Exception {
        String owner = registerVerifyAndLogin();
        String user = registerVerifyAndLogin();
        long id = createCommunity(owner);

        assertThat(joined(user)).doesNotContain(id);
        assertThat(memberCount(owner, id)).isEqualTo(1);

        assertThat(call(HttpMethod.POST, "/api/communities/" + id + "/join", user, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(joined(user)).contains(id);
        assertThat(memberCount(owner, id)).isEqualTo(2);

        assertThat(call(HttpMethod.POST, "/api/communities/" + id + "/leave", user, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(joined(user)).doesNotContain(id);
        assertThat(memberCount(owner, id)).isEqualTo(1);
    }

    @Test
    void joinTwice_isIdempotent_andLeaveWhenNotMember_isHarmless() throws Exception {
        String owner = registerVerifyAndLogin();
        String user = registerVerifyAndLogin();
        long id = createCommunity(owner);

        call(HttpMethod.POST, "/api/communities/" + id + "/join", user, null);
        assertThat(call(HttpMethod.POST, "/api/communities/" + id + "/join", user, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(memberCount(owner, id)).isEqualTo(2);
        assertThat(joined(user).stream().filter(x -> x == id).count()).isEqualTo(1);

        call(HttpMethod.POST, "/api/communities/" + id + "/leave", user, null);
        assertThat(call(HttpMethod.POST, "/api/communities/" + id + "/leave", user, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(memberCount(owner, id)).isEqualTo(1);
    }

    @Test
    void postingIntoCommunity_requiresMembership() throws Exception {
        String owner = registerVerifyAndLogin();
        String user = registerVerifyAndLogin();
        long id = createCommunity(owner);
        Map<String, Object> post = Map.of("title", "t", "text", "hello", "communityId", id);

        ResponseEntity<String> denied = call(HttpMethod.POST, "/api/posts", user, post);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(denied.getBody()).contains("join the community");

        call(HttpMethod.POST, "/api/communities/" + id + "/join", user, null);
        assertThat(call(HttpMethod.POST, "/api/posts", user, post).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** The home feed is cached per user; joining/leaving must not leave it stale. */
    @Test
    void homeFeed_reflectsJoinAndLeaveImmediately() throws Exception {
        String owner = registerVerifyAndLogin();
        String user = registerVerifyAndLogin();
        long id = createCommunity(owner);
        long postId = json(call(HttpMethod.POST, "/api/posts", owner,
                Map.of("title", "t", "text", "community post", "communityId", id))).get("id").asLong();
        // Age the post past PostService.MODERATION_WINDOW so feed pages containing it are cacheable.
        jdbcTemplate.update("UPDATE posts SET created_at = now() - interval '1 hour' WHERE id = ?", postId);

        String home = "/api/posts/home?sort=new&size=50";
        assertThat(ids(call(HttpMethod.GET, home, user, null))).doesNotContain(postId); // primes cache

        call(HttpMethod.POST, "/api/communities/" + id + "/join", user, null);
        assertThat(ids(call(HttpMethod.GET, home, user, null))).contains(postId);

        call(HttpMethod.POST, "/api/communities/" + id + "/leave", user, null);
        assertThat(ids(call(HttpMethod.GET, home, user, null))).doesNotContain(postId);
    }
}
