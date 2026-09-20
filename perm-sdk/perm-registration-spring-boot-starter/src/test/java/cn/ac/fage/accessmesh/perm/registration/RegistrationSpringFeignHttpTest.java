package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.registration.config.PermRegistrationAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/** 使用生产 Spring Feign encoder/decoder 与真实 HTTP，避免 mock 响应壳掩盖解码问题。 */
class RegistrationSpringFeignHttpTest {
    @Test void shouldEncodeCredentialRequestAndDecodeEnvelopeThroughAutoConfiguredFeign() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> requestBody=new AtomicReference<>();
        AtomicReference<List<String>> headers=new AtomicReference<>();
        server.createContext("/api/access/integration/permission-manifest/full-sync",exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            headers.set(List.of(exchange.getRequestHeaders().getFirst("X-Tenant-Id"),exchange.getRequestHeaders().getFirst("X-Service-Code"),
                    exchange.getRequestHeaders().getFirst("X-Credential-Id"),exchange.getRequestHeaders().getFirst("X-Credential-Secret")));
            byte[] body="{\"code\":200,\"message\":\"success\",\"data\":{\"accepted\":true,\"applied\":true,\"stale\":false,\"retryClass\":null,\"reason\":null,\"detail\":{\"appliedCount\":0,\"staleCount\":0,\"failedCount\":0,\"deactivatedCount\":0,\"itemResults\":[]}},\"requestId\":\"r\",\"traceId\":\"t\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
                    JacksonAutoConfiguration.class,HttpMessageConvertersAutoConfiguration.class,ValidationAutoConfiguration.class,
                    FeignAutoConfiguration.class,PermRegistrationAutoConfiguration.class))
                    .withPropertyValues("perm.registration.enabled=true","perm.allow-insecure=true",
                            "perm.credential-id=default-id","perm.credential-secret=default-secret",
                            "spring.cloud.openfeign.client.config.permissionRegistration.url=http://127.0.0.1:"+server.getAddress().getPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var result=context.getBean(PermissionRegistrationPublisher.class).publish(
                                new RegistrationTarget(7L,"reports","tenant-id","tenant-secret"),new PermissionManifestReq(1,"42","r",List.of()));
                        assertThat(result.accepted()).isTrue();
                        assertThat(result.detail().failedCount()).isZero();
                        assertThat(requestBody.get()).contains("\"publicationGeneration\":\"42\"");
                        assertThat(headers.get()).containsExactly("7","reports","tenant-id","tenant-secret");
                    });
        } finally { server.stop(0); }
    }
}
