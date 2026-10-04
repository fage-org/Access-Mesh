package cn.ac.fage.accessmesh.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2M 凭证端点白名单单源回归锁（T-PERM-070）。
 * <p>锁定：method+精确路径双因子匹配、无通配（前缀/相邻命名空间不得命中）、
 * 阶段一三端点基线（071 manifest 端点预留）＋ T-ACCESS-059 操作准入两端点
 * （运行时查询族首批凭证化端点，Q-040 收敛方向）。</p>
 */
class M2mCredentialEndpointsTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "/api/access/auth/check",
        "/api/access/auth/batch-check",
        "/api/access/auth/query-resources",
        "/api/access/auth/query-scopes",
        "/api/access/abstract-user/sync",
        "/api/access/abstract-user/full-sync",
        "/api/access/abstract-role/sync",
        "/api/access/abstract-role/full-sync",
        "/api/access/user-role/sync",
        "/api/access/user-role/full-sync"})
    void shouldMatchRuntimeQueriesAndRemainingSync(String path) {
        assertThat(M2mCredentialEndpoints.matches("POST", path)).isTrue();
        assertThat(M2mCredentialEndpoints.matches("GET", path)).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", path + "/other")).isFalse();
    }

    @Test
    @DisplayName("阶段一三端点精确命中")
    void shouldMatchStageOneEndpoints() {
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/resource-entity/sync")).isTrue();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/resource-entity/full-sync")).isTrue();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/integration/permission-manifest/full-sync")).isTrue();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/service-config/sync-v2")).isTrue();
        // T-ACCESS-059：操作准入两端点（凭证调用；网关沿用内部密钥平台信任域形态）
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/auth/interface-admission")).isTrue();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/auth/interface-admission-snapshot")).isTrue();
        // method 大小写不敏感
        assertThat(M2mCredentialEndpoints.matches("post", "/api/access/resource-entity/sync")).isTrue();
    }

    @Test
    @DisplayName("负向：前缀扩展/相邻命名空间/其他端点/其他方法不命中（无通配语义锁）")
    void shouldNotMatchAnythingElse() {
        // 前缀扩展（防未来误改 startsWith）
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/resource-entity/sync/extra")).isFalse();
        // 相邻命名空间
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/resource-entity/create")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/service-config/sync")).isFalse();
        // 管理与查询端点（凭证能力半径边界；旧 auth 查询族维持凭证外——check/interface 旧端点归 062 退役）
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/service-credential/list")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/auth/check-interface")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/auth/interface-snapshot")).isFalse();
        // 前缀相邻不命中（admission 非 admission-snapshot 的兄弟路径不存在通配）
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/auth/interface-admission/extra")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/service-credential/list")).isFalse();
        // 方法不匹配
        assertThat(M2mCredentialEndpoints.matches("GET", "/api/access/resource-entity/sync")).isFalse();
        // null 安全
        assertThat(M2mCredentialEndpoints.matches(null, "/api/access/resource-entity/sync")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", null)).isFalse();
    }

    @Test
    @DisplayName("清单保持精确端点边界")
    void shouldExposeImmutableBaseline() {
        assertThat(M2mCredentialEndpoints.endpoints()).hasSize(16);
        assertThat(M2mCredentialEndpoints.endpoints())
            .extracting(M2mCredentialEndpoints.M2mEndpoint::path)
            .containsExactlyInAnyOrder(
                "/api/access/resource-entity/sync",
                "/api/access/resource-entity/full-sync",
                "/api/access/integration/permission-manifest/full-sync",
                "/api/access/service-config/sync-v2",
                "/api/access/auth/interface-admission",
                "/api/access/auth/interface-admission-snapshot",
                "/api/access/auth/check",
                "/api/access/auth/batch-check",
                "/api/access/auth/query-resources",
                "/api/access/auth/query-scopes",
                "/api/access/abstract-user/sync",
                "/api/access/abstract-user/full-sync",
                "/api/access/abstract-role/sync",
                "/api/access/abstract-role/full-sync",
                "/api/access/user-role/sync",
                "/api/access/user-role/full-sync");
    }
}
