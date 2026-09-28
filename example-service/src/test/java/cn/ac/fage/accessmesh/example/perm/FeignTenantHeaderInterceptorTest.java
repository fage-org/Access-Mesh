package cn.ac.fage.accessmesh.example.perm;

import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户头注入拦截器单测（T-ACCESS-061）：仅 /api/access/ 路径注入、上下文未绑定不注入。
 */
class FeignTenantHeaderInterceptorTest {

    private final FeignTenantHeaderInterceptor interceptor = new FeignTenantHeaderInterceptor();

    @Test
    @DisplayName("access 路径 + 上下文已绑定：注入 X-Tenant-Id")
    void injectsTenantHeaderOnAccessPath() {
        PermCallContext.setTenantId("7");
        try {
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template.uri("/api/access/auth/check"));
            assertThat(template.headers().get("X-Tenant-Id")).containsExactly("7");
        } finally {
            PermCallContext.clear();
        }
    }

    @Test
    @DisplayName("非 access 路径不注入；上下文未绑定不注入")
    void skipsNonAccessPathOrUnboundContext() {
        PermCallContext.setTenantId("7");
        try {
            RequestTemplate other = new RequestTemplate();
            interceptor.apply(other.uri("/api/other/thing"));
            assertThat(other.headers()).doesNotContainKey("X-Tenant-Id");

            PermCallContext.clear();
            RequestTemplate unbound = new RequestTemplate();
            interceptor.apply(unbound.uri("/api/access/auth/check"));
            assertThat(unbound.headers()).doesNotContainKey("X-Tenant-Id");
        } finally {
            PermCallContext.clear();
        }
    }
}
