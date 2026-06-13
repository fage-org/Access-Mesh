package cn.ac.fage.accessmesh.perm.client.feign;

import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link FeignInternalSyncInterceptor} 仅对 {@code /api/perm/**} 路径注入
 * {@code X-Internal-Secret} 与 {@code X-Service-Code}，且不覆盖调用方已显式声明的
 * {@code X-Service-Code}。
 */
class FeignInternalSyncInterceptorTest {

    private FeignInternalSyncInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new FeignInternalSyncInterceptor();
        setField("internalSecret", "test-secret-xyz");
        setField("serviceCode", "admin-service");
    }

    private void setField(String name, String value) throws Exception {
        Field f = FeignInternalSyncInterceptor.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(interceptor, value);
    }

    private RequestTemplate template(String url) {
        RequestTemplate t = new RequestTemplate();
        t.method(feign.Request.HttpMethod.POST);
        t.uri(url);
        return t;
    }

    @Test
    void apply_injectsSecretAndServiceCode_forSyncPath() {
        RequestTemplate t = template("/api/perm/abstract-user/sync");

        interceptor.apply(t);

        assertThat(t.headers().get("X-Internal-Secret")).containsExactly("test-secret-xyz");
        assertThat(t.headers().get("X-Service-Code")).containsExactly("admin-service");
    }

    @Test
    void apply_injectsSecretAndServiceCode_forAuthCheckPath() {
        // PermissionFeignClient.authCheck 也走 /api/perm/auth/check；同样注入是合理副作用
        // 现有 Gateway 链路本身也通过 X-Internal-Secret 校验，此处统一注入即可
        RequestTemplate t = template("/api/perm/auth/check");

        interceptor.apply(t);

        assertThat(t.headers().get("X-Internal-Secret")).containsExactly("test-secret-xyz");
        assertThat(t.headers().get("X-Service-Code")).containsExactly("admin-service");
    }

    @Test
    void apply_preservesExistingServiceCode_whenCallerProvidesOne() {
        RequestTemplate t = template("/api/perm/abstract-role/full-sync");
        // 调用方已显式声明（向后兼容场景）
        t.header("X-Service-Code", "external-importer");

        interceptor.apply(t);

        // X-Internal-Secret 仍注入
        assertThat(t.headers().get("X-Internal-Secret")).containsExactly("test-secret-xyz");
        // X-Service-Code 保留原值，不被默认值覆盖
        assertThat(t.headers().get("X-Service-Code")).containsExactly("external-importer");
    }

    @Test
    void apply_skipsNonSyncPath() {
        RequestTemplate t = template("/some/other/api");

        interceptor.apply(t);

        assertThat(t.headers().get("X-Internal-Secret")).isNull();
        assertThat(t.headers().get("X-Service-Code")).isNull();
    }

    @Test
    void apply_doesNotInjectSecret_whenSecretIsBlank() throws Exception {
        setField("internalSecret", "");
        RequestTemplate t = template("/api/perm/abstract-user/sync");

        interceptor.apply(t);

        assertThat(t.headers().get("X-Internal-Secret")).isNull();
        // service-code 仍按默认注入（保持 perm-sdk 在没配置 secret 时被禁用，本测试只为防御性兜底）
        assertThat(t.headers().get("X-Service-Code")).containsExactly("admin-service");
    }
}
