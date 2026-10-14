package cn.ac.fage.accessmesh.perm.common.feign;

import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 凭证注入路径起点回归锁（T-ACCESS-098）：M2M 清单匹配要求 /api/access/ 片段位于
 * 真正路径起点（相对路径形态、完整值匹配）——URL 其他位置出现该子串不注入：
 * 异域同路径（绝对 URL 指向任意授权域）、前缀包含（子串嵌在更长路径中）、
 * query 携带（子串仅在 query 参数值里）。三反例在旧 indexOf 实现下失败（红跑实证）。
 * 外评 P2-1 处置后注入以目标接口为判据——模板须挂 SDK 接口元信息（真实链路经
 * MethodMetadata 构造自绑定 + RequestTemplate 拷贝构造保留，字节码实证）。
 */
class FeignCredentialInterceptorPathStartTest {

    /** 与 SDK 客户端同简名的桩接口（configKey=简名#方法），驱动接口绑定门。 */
    interface PermissionFeignClient {
        @feign.RequestLine("POST /stub")
        void stub();
    }

    private static feign.MethodMetadata sdkMetadata() {
        return new feign.Contract.Default()
            .parseAndValidateMetadata(PermissionFeignClient.class).get(0);
    }

    private FeignCredentialInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new FeignCredentialInterceptor();
        ReflectionTestUtils.setField(interceptor, "credentialId", "sc-a");
        ReflectionTestUtils.setField(interceptor, "credentialSecret", "sk-b");
        ReflectionTestUtils.setField(interceptor, "allowInsecure", "true");
    }

    private RequestTemplate post(String uri) {
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        template.uri(uri);
        template.methodMetadata(sdkMetadata());
        interceptor.apply(template);
        return template;
    }

    @Test
    @DisplayName("正向对照：真实路径起点（发现制相对路径形态）→ 注入双凭证头")
    void shouldInject_whenPathStartsAtTrueBeginning() {
        RequestTemplate template = post("/api/access/auth/check");
        assertThat(template.headers())
            .containsEntry(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID, java.util.List.of("sc-a"))
            .containsEntry(FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET, java.util.List.of("sk-b"));
    }

    @Test
    @DisplayName("异域同路径（绝对 URL 形态）：feign 框架契约锁——RequestTemplate.uri() 拒绝绝对 URL，"
        + "拦截器可见的 url() 结构性不可为绝对路径（异域场景的真实形态=宿主其他 Feign 客户端，见装配隔离测试）")
    void shouldNeverSeeAbsoluteUrl_frameworkContract() {
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> template.uri("http://other.example.com:9000/api/access/auth/check"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absolute");
    }

    @Test
    @DisplayName("前缀包含：子串嵌在更长路径中（非路径起点）→ 不注入")
    void shouldNotInject_whenMarkerInsideLongerPath() {
        for (String uri : java.util.List.of(
            "/v1/proxy/api/access/auth/check",
            "/internal/api/access/resource-entity/full-sync")) {
            RequestTemplate template = post(uri);
            assertThat(template.headers()).as("路径 %s 不得携带凭证头", uri)
                .doesNotContainKeys(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID,
                    FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET);
        }
    }

    @Test
    @DisplayName("query 携带：子串仅在 query 参数值里 → 不注入（可重放 secret 不外发到任意 query 形态调用）")
    void shouldNotInject_whenMarkerOnlyInQueryValue() {
        RequestTemplate template = post("/fetch?next=/api/access/auth/check");
        assertThat(template.headers())
            .doesNotContainKeys(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID,
                FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET);
    }
}
