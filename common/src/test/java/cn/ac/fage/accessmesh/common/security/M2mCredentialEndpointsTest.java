package cn.ac.fage.accessmesh.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2M 凭证端点白名单单源回归锁（T-PERM-070）。
 * <p>锁定：method+精确路径双因子匹配、无通配（前缀/相邻命名空间不得命中）、
 * 阶段一三端点基线（071 manifest 端点预留）。</p>
 */
class M2mCredentialEndpointsTest {

    @Test
    @DisplayName("阶段一三端点精确命中")
    void shouldMatchStageOneEndpoints() {
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/resource-entity/sync")).isTrue();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/resource-entity/full-sync")).isTrue();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/integration/permission-manifest/full-sync")).isTrue();
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
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/abstract-user/full-sync")).isFalse();
        // 管理与查询端点（凭证能力半径边界）
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/auth/query-resources")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", "/api/access/service-credential/list")).isFalse();
        // 方法不匹配
        assertThat(M2mCredentialEndpoints.matches("GET", "/api/access/resource-entity/sync")).isFalse();
        // null 安全
        assertThat(M2mCredentialEndpoints.matches(null, "/api/access/resource-entity/sync")).isFalse();
        assertThat(M2mCredentialEndpoints.matches("POST", null)).isFalse();
    }

    @Test
    @DisplayName("清单不可变且恰三条（阶段一基线计数锁）")
    void shouldExposeImmutableBaseline() {
        assertThat(M2mCredentialEndpoints.endpoints()).hasSize(3);
        assertThat(M2mCredentialEndpoints.endpoints())
            .extracting(M2mCredentialEndpoints.M2mEndpoint::path)
            .containsExactlyInAnyOrder(
                "/api/access/resource-entity/sync",
                "/api/access/resource-entity/full-sync",
                "/api/access/integration/permission-manifest/full-sync");
    }
}
