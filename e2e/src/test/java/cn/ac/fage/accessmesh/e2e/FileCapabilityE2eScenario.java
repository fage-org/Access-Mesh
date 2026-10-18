package cn.ac.fage.accessmesh.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;

/** 复用已有真实 Gateway 进程；文件数据和角色独立，不借用主旅程授权。 */
final class FileCapabilityE2eScenario {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().configureForJackson2().build();
    private FileCapabilityE2eScenario() {}

    static void verify(String base, String rootPassword, Function<String, String> captcha) throws Exception {
        String password = "File-E2e-Customer-2026!";
        E2eTenantSupport.openTenant(base, rootPassword, "file-e2e", password, captcha);
        String admin = E2eTenantSupport.login(base, "file-e2e", "admin", password, captcha).path("accessToken").asText();
        long document = upload(base, admin, "document");
        long other = upload(base, admin, "file-e2e-new");
        JsonNode info = post(base, admin, "file/detail", Map.of("id", document));
        assertThat(info.path("bizType").asText()).isEqualTo("document");
        assertThat(info.path("originalName").asText()).isEqualTo("file-e2e.txt");
        assertThat(download(base, admin, document).body()).isEqualTo("file-e2e-content");
        assertThat(post(base, admin, "auth/user-menu", Map.of()).toString()).contains("/system/file");

        JsonNode user = post(base, admin, "user/create", Map.of("username", "file-reader", "name", "File reader"));
        JsonNode first = E2eTenantSupport.login(base, "file-e2e", "file-reader", user.path("initialPassword").asText(), captcha);
        post(base, first.path("accessToken").asText(), "user/reset-password", Map.of("userId", user.path("id").asLong(), "newPassword", password));
        String reader = E2eTenantSupport.login(base, "file-e2e", "file-reader", password, captcha).path("accessToken").asText();
        assertThat(download(base, reader, document).statusCode()).isEqualTo(403);
        post(base, admin, "abstract-role/create", Map.of("roleTypeCode", "BASIC_ROLE", "externalId", "file-reader", "name", "File reader"));
        post(base, admin, "user-role/assign", Map.of("items", List.of(Map.of(
            "subjectTypeCode", "LOCAL_USER", "subjectExternalId", user.path("id").asText(),
            "roleTypeCode", "BASIC_ROLE", "roleExternalId", "file-reader"))));
        JsonNode grant = post(base, admin, "role-resource-permission/apply-grant-plan", Map.of(
            "roleTypeCode", "BASIC_ROLE", "roleExternalId", "file-reader", "plan", Map.of("creates", List.of(Map.of("key", Map.of(
                "resourceTypeCode", "ADMIN_FILE", "resourceCode", "document", "codeType", "default",
                "operationCode", "VIEW", "scopeMode", "INSTANCE", "canGrant", false))))));
        awaitDownload(base, reader, document, true);
        JsonNode page = post(base, reader, "file/page", Map.of("pageNum", 1, "pageSize", 20));
        assertThat(page.path("items").size()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(document);
        assertThat(JSON.readTree(download(base, reader, other).body()).path("code").asInt()).isNotEqualTo(200);
        assertThat(send(base, reader, "file/delete", Map.of("ids", List.of(document))).statusCode()).isEqualTo(403);
        assertThat(uploadResponse(base, reader, "document").statusCode()).isEqualTo(403);

        E2eTenantSupport.openTenant(base, rootPassword, "file-e2e-other", password, captcha);
        String foreign = E2eTenantSupport.login(base, "file-e2e-other", "admin", password, captcha).path("accessToken").asText();
        assertThat(post(base, foreign, "file/page", Map.of()).path("items").isEmpty()).isTrue();
        assertThat(JSON.readTree(download(base, foreign, document).body()).path("code").asInt()).isNotEqualTo(200);
        post(base, admin, "role-resource-permission/apply-grant-plan", Map.of("roleTypeCode", "BASIC_ROLE",
            "roleExternalId", "file-reader", "plan", Map.of("removes", List.of(grant.path("items").get(0).path("id").asLong()))));
        awaitDownload(base, reader, document, false);
        post(base, admin, "file/delete", Map.of("ids", List.of(document, other)));
        assertThat(post(base, admin, "file/page", Map.of()).path("items").isEmpty()).isTrue();
    }

    private static JsonNode post(String base, String token, String path, Object body) {
        return E2eTenantSupport.post(base + "/api/access/" + path, token, body);
    }
    private static HttpResponse<String> send(String base, String token, String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + "/api/access/" + path)).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> download(String base, String token, long id) throws Exception {
        return send(base, token, "file/download", Map.of("id", id));
    }
    private static long upload(String base, String token, String category) throws Exception {
        var response = uploadResponse(base, token, category);
        assertThat(response.statusCode()).isEqualTo(200);
        var envelope = JSON.readTree(response.body());
        assertThat(envelope.path("code").asInt()).as(response.body()).isEqualTo(200);
        return envelope.path("data").asLong();
    }
    private static HttpResponse<String> uploadResponse(String base, String token, String category) throws Exception {
        String boundary = "AccessMeshFileE2eBoundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"bizType\"\r\n\r\n" + category
            + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"file-e2e.txt\""
            + "\r\nContent-Type: text/plain\r\n\r\nfile-e2e-content\r\n--" + boundary + "--\r\n";
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + "/api/access/file/upload")).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void awaitDownload(String base, String token, long id, boolean allowed) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        HttpResponse<String> response;
        do {
            response = download(base, token, id);
            if (allowed ? response.body().equals("file-e2e-content") : response.statusCode() == 403) return;
            Thread.sleep(200); // 有上界的条件轮询；不以固定 sleep 余量断言缓存已失效。
        } while (System.nanoTime() < deadline);
        throw new AssertionError("文件授权变更未在 30 秒内生效：" + response.statusCode() + " " + response.body());
    }
}
