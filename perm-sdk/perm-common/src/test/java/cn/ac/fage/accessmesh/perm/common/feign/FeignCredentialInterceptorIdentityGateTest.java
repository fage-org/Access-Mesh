package cn.ac.fage.accessmesh.perm.common.feign;

import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 凭证注入身份门回归锁（codex sol r2 P2-1，T-ACCESS-098 二轮补强）：
 * 注入判据=目标接口全限定类名（{@code MethodMetadata.targetType().getName()}），
 * 同简名异包接口不得通过——旧实现按 feign configKey（接口简名#方法）前缀比对，
 * {@code acme.PermissionFeignClient} 等宿主同简名接口可穿过简名门禁取得可重放
 * 服务凭证（Feign 13.2.1 {@code Feign.configKey} 仅取 {@code getSimpleName()}，
 * 本类内嵌同名接口在旧实现下红跑实证注入）。
 */
class FeignCredentialInterceptorIdentityGateTest {

    /** 同简名、异包（嵌套于本测试类）的宿主形态接口——旧简名门禁的穿透反例。 */
    interface PermissionFeignClient {
        @feign.RequestLine("POST /stub")
        void stub();
    }

    private static feign.MethodMetadata sameSimpleNameMetadata() {
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

    private RequestTemplate m2mPost(feign.MethodMetadata metadata) {
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        template.uri("/api/access/auth/check");
        template.methodMetadata(metadata);
        interceptor.apply(template);
        return template;
    }

    @Test
    @DisplayName("同简名异包接口（M2M 精确端点、完整路径匹配）→ 不注入：身份门按全限定名")
    void sameSimpleNameDifferentPackageInterfaceDoesNotReceiveCredentials() {
        RequestTemplate template = m2mPost(sameSimpleNameMetadata());
        assertThat(template.headers())
            .doesNotContainKeys(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID,
                FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET);
    }

    @Test
    @DisplayName("无 MethodMetadata 的手工模板 → 不注入（非 SDK 链路）")
    void templateWithoutMetadataDoesNotReceiveCredentials() {
        RequestTemplate template = new RequestTemplate();
        template.method("POST");
        template.uri("/api/access/auth/check");
        interceptor.apply(template);
        assertThat(template.headers())
            .doesNotContainKeys(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID,
                FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET);
    }

    @Test
    @DisplayName("有 configKey 但 targetType 未绑定的模板 → 不注入（fail-closed，不回退简名判定）")
    void templateWithoutTargetTypeDoesNotReceiveCredentials() {
        feign.MethodMetadata metadata = sameSimpleNameMetadata();
        metadata.targetType(null);
        RequestTemplate template = m2mPost(metadata);
        assertThat(template.headers())
            .doesNotContainKeys(FeignCredentialInterceptor.HEADER_CREDENTIAL_ID,
                FeignCredentialInterceptor.HEADER_CREDENTIAL_SECRET);
    }
}
