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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the UI relies on beyond the feed: delete rights (author / community owner / admin)
 * and the {@code canDelete} flags that drive the buttons, threaded comment replies,
 * community deletion, public community browsing and image uploads.
 */
class ContentManagementIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender javaMailSender;

    // 1x1 transparent PNG
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

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

    private JsonNode ok(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return json(response);
    }

    private String registerVerifyAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "m-" + suffix + "@test.com";
        long userId = ok(call(HttpMethod.POST, "/api/users", null,
                Map.of("username", "muser-" + suffix, "email", email, "password", "Password12!"))).get("id").asLong();
        String token = emailVerificationTokenRepository.findTopByUser_IdOrderByIdDesc(userId).orElseThrow().getToken();
        restTemplate.getForEntity(url("/api/auth/verify?token=" + token), String.class);
        return ok(call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", email, "password", "Password12!"))).get("access_token").asText();
    }

    private JsonNode createCommunity(String token) throws Exception {
        String name = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return ok(call(HttpMethod.POST, "/api/communities", token,
                Map.of("name", name, "description", "test", "isPrivate", false)));
    }

    private long post(String token, Long communityId, String text) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", "t", "text", text));
        if (communityId != null) body.put("communityId", communityId);
        return ok(call(HttpMethod.POST, "/api/posts", token, body)).get("id").asLong();
    }

    private long comment(String token, long postId, Long parentId, String text) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("postId", postId, "text", text));
        if (parentId != null) body.put("parentCommentId", parentId);
        return ok(call(HttpMethod.POST, "/api/comments", token, body)).get("id").asLong();
    }

    private JsonNode findPost(JsonNode page, long id) {
        for (JsonNode n : page.get("content")) if (n.get("id").asLong() == id) return n;
        return null;
    }

    private ResponseEntity<String> upload(String token, byte[] bytes, String filename) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) h.setBearerAuth(token);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        return restTemplate.exchange(url("/api/media"), HttpMethod.POST, new HttpEntity<>(form, h), String.class);
    }

    // ── Post deletion & canDelete ─────────────────────────────────────────────

    @Test
    void communityOwner_canDeleteMembersPost_outsiderCannot() throws Exception {
        String owner = registerVerifyAndLogin();
        String member = registerVerifyAndLogin();
        String outsider = registerVerifyAndLogin();
        JsonNode c = createCommunity(owner);
        long cid = c.get("id").asLong();
        call(HttpMethod.POST, "/api/communities/" + cid + "/join", member, null);
        long postId = post(member, cid, "member post");

        String feed = "/api/posts/community/" + c.get("name").asText();
        assertThat(findPost(ok(call(HttpMethod.GET, feed, owner, null)), postId).get("canDelete").asBoolean()).isTrue();
        assertThat(findPost(ok(call(HttpMethod.GET, feed, member, null)), postId).get("canDelete").asBoolean()).isTrue();
        assertThat(findPost(ok(call(HttpMethod.GET, feed, outsider, null)), postId).get("canDelete").asBoolean()).isFalse();
        assertThat(findPost(ok(call(HttpMethod.GET, feed, null, null)), postId).get("canDelete").asBoolean()).isFalse();

        ResponseEntity<String> denied = call(HttpMethod.DELETE, "/api/posts/" + postId, outsider, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(call(HttpMethod.DELETE, "/api/posts/" + postId, owner, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findPost(ok(call(HttpMethod.GET, feed, owner, null)), postId)).isNull();
        assertThat(call(HttpMethod.GET, "/api/posts/" + postId, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void author_canDeleteOwnProfilePost_othersCannot() throws Exception {
        String author = registerVerifyAndLogin();
        String other = registerVerifyAndLogin();
        long postId = post(author, null, "profile post");

        assertThat(ok(call(HttpMethod.GET, "/api/posts/" + postId, other, null)).get("canDelete").asBoolean()).isFalse();
        assertThat(call(HttpMethod.DELETE, "/api/posts/" + postId, other, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ok(call(HttpMethod.GET, "/api/posts/" + postId, author, null)).get("canDelete").asBoolean()).isTrue();
        assertThat(call(HttpMethod.DELETE, "/api/posts/" + postId, author, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void admin_canDeleteAnyPost() throws Exception {
        String author = registerVerifyAndLogin();
        String admin = ok(call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "admin@example.com", "password", "Password123!"))).get("access_token").asText();
        long postId = post(author, null, "anything");

        assertThat(ok(call(HttpMethod.GET, "/api/posts/" + postId, admin, null)).get("canDelete").asBoolean()).isTrue();
        assertThat(call(HttpMethod.DELETE, "/api/posts/" + postId, admin, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Threaded comments ────────────────────────────────────────────────────

    @Test
    void replies_areNestedThreeLevelsDeep() throws Exception {
        String a = registerVerifyAndLogin();
        String b = registerVerifyAndLogin();
        long postId = post(a, null, "thread");
        long root = comment(a, postId, null, "root");
        long reply = comment(b, postId, root, "reply");
        long replyToReply = comment(a, postId, reply, "reply to reply");
        long sibling = comment(b, postId, root, "second reply");

        JsonNode roots = ok(call(HttpMethod.GET, "/api/posts/" + postId + "/comments?sort=old", a, null)).get("content");
        assertThat(roots).hasSize(1);
        JsonNode r = roots.get(0);
        assertThat(r.get("id").asLong()).isEqualTo(root);
        assertThat(r.get("children")).hasSize(2);
        assertThat(r.get("children").get(0).get("id").asLong()).isEqualTo(reply);
        assertThat(r.get("children").get(1).get("id").asLong()).isEqualTo(sibling);
        JsonNode deep = r.get("children").get(0).get("children");
        assertThat(deep).hasSize(1);
        assertThat(deep.get(0).get("id").asLong()).isEqualTo(replyToReply);
        assertThat(deep.get(0).get("text").asText()).isEqualTo("reply to reply");

        // canDelete per viewer: a wrote root + replyToReply, b wrote the others (profile post → no mods)
        assertThat(r.get("canDelete").asBoolean()).isTrue();
        assertThat(r.get("children").get(0).get("canDelete").asBoolean()).isFalse();
        assertThat(ok(call(HttpMethod.GET, "/api/posts/" + postId, a, null)).get("commentCount").asLong()).isEqualTo(4);
    }

    @Test
    void replyToCommentOfAnotherPost_isRejected() throws Exception {
        String a = registerVerifyAndLogin();
        long p1 = post(a, null, "one");
        long p2 = post(a, null, "two");
        long c1 = comment(a, p1, null, "on p1");

        ResponseEntity<String> res = call(HttpMethod.POST, "/api/comments", a,
                Map.of("postId", p2, "parentCommentId", c1, "text", "cross"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deletedComment_keepsItsReplies_andOwnerCanModerateComments() throws Exception {
        String owner = registerVerifyAndLogin();
        String member = registerVerifyAndLogin();
        String outsider = registerVerifyAndLogin();
        long cid = createCommunity(owner).get("id").asLong();
        call(HttpMethod.POST, "/api/communities/" + cid + "/join", member, null);
        long postId = post(member, cid, "post");
        long root = comment(member, postId, null, "root by member");
        comment(outsider, postId, root, "reply by outsider");

        assertThat(call(HttpMethod.DELETE, "/api/comments/" + root, outsider, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.DELETE, "/api/comments/" + root, owner, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode r = ok(call(HttpMethod.GET, "/api/posts/" + postId + "/comments", owner, null)).get("content").get(0);
        assertThat(r.get("isDeleted").asBoolean()).isTrue();
        assertThat(r.get("text").asText()).isEqualTo("[deleted]");
        assertThat(r.get("canDelete").asBoolean()).isFalse();
        assertThat(r.get("children")).hasSize(1);
        assertThat(r.get("children").get(0).get("text").asText()).isEqualTo("reply by outsider");
    }

    @Test
    void cannotCommentOnDeletedPost() throws Exception {
        String a = registerVerifyAndLogin();
        long postId = post(a, null, "gone soon");
        call(HttpMethod.DELETE, "/api/posts/" + postId, a, null);

        assertThat(call(HttpMethod.POST, "/api/comments", a, Map.of("postId", postId, "text", "hi"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Community page & deletion ─────────────────────────────────────────────

    @Test
    void communityPage_isPublic() throws Exception {
        String owner = registerVerifyAndLogin();
        JsonNode c = createCommunity(owner);
        String name = c.get("name").asText();

        JsonNode info = ok(call(HttpMethod.GET, "/api/communities/" + name, null, null));
        assertThat(info.get("id").asLong()).isEqualTo(c.get("id").asLong());
        assertThat(info.get("memberCount").asLong()).isEqualTo(1);
        assertThat(call(HttpMethod.GET, "/api/communities", null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/posts/community/" + name, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // writes still need login
        assertThat(call(HttpMethod.POST, "/api/communities/" + info.get("id").asLong() + "/join", null, null)
                .getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void owner_canDeleteCommunity_postsDisappear_othersCannot() throws Exception {
        String owner = registerVerifyAndLogin();
        String member = registerVerifyAndLogin();
        JsonNode c = createCommunity(owner);
        long cid = c.get("id").asLong();
        call(HttpMethod.POST, "/api/communities/" + cid + "/join", member, null);
        long postId = post(member, cid, "will vanish");

        assertThat(call(HttpMethod.DELETE, "/api/communities/" + cid, member, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(call(HttpMethod.DELETE, "/api/communities/" + cid, owner, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        List<Long> ids = new ArrayList<>();
        ok(call(HttpMethod.GET, "/api/communities", owner, null)).forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).doesNotContain(cid);
        assertThat(call(HttpMethod.GET, "/api/communities/" + c.get("name").asText(), owner, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(findPost(ok(call(HttpMethod.GET, "/api/posts/global?sort=new&size=100", member, null)), postId)).isNull();
        assertThat(call(HttpMethod.GET, "/api/posts/" + postId, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.DELETE, "/api/communities/" + cid, owner, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Images ────────────────────────────────────────────────────────────────

    @Test
    void uploadImage_thenPostWithIt_andServeIt() throws Exception {
        String token = registerVerifyAndLogin();

        JsonNode up = ok(upload(token, PNG, "pixel.png"));
        String mediaUrl = up.get("url").asText();
        assertThat(mediaUrl).matches("^/media/[0-9a-f-]{36}\\.png$");

        ResponseEntity<byte[]> served = restTemplate.getForEntity(url(mediaUrl), byte[].class);
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(served.getBody()).isEqualTo(PNG);

        JsonNode created = ok(call(HttpMethod.POST, "/api/posts", token, Map.of("title", "pic", "text", "",
                "media", List.of(Map.of("mediaUrl", mediaUrl, "mediaType", "IMAGE")))));
        JsonNode fetched = ok(call(HttpMethod.GET, "/api/posts/" + created.get("id").asLong(), null, null));
        assertThat(fetched.get("media")).hasSize(1);
        assertThat(fetched.get("media").get(0).get("mediaUrl").asText()).isEqualTo(mediaUrl);
    }

    @Test
    void upload_rejectsNonImages_spoofedNames_andAnonymous() throws Exception {
        String token = registerVerifyAndLogin();

        assertThat(upload(token, "<svg onload=alert(1)>".getBytes(), "x.png").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(token, "hello".getBytes(), "x.txt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(null, PNG, "pixel.png").getStatusCode().is4xxClientError()).isTrue();
        assertThat(upload(token, new byte[6 * 1024 * 1024], "big.png").getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void media_pathTraversalAndUnknownNames_are404() {
        assertThat(restTemplate.getForEntity(url("/media/..%2Fapplication.properties"), String.class)
                .getStatusCode().is4xxClientError()).isTrue();
        assertThat(restTemplate.getForEntity(url("/media/" + UUID.randomUUID() + ".png"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void post_rejectsUnsafeMediaUrls() throws Exception {
        String token = registerVerifyAndLogin();
        for (String bad : List.of("javascript:alert(1)", "data:image/png;base64,AAAA", "")) {
            ResponseEntity<String> res = call(HttpMethod.POST, "/api/posts", token, Map.of("title", "x", "text", "y",
                    "media", List.of(Map.of("mediaUrl", bad, "mediaType", "IMAGE"))));
            assertThat(res.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
