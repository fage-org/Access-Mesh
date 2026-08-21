package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 授权缓存目录与安全边界启动校验测试（T-ACCESS-008）。
 * <p>
 * 快照链路 6 目录（用户决策 2026-08-21）必须 L2_ONLY 且 TTL=10s（不创建授权 L1）；
 * OPERATION_PERMISSIONS_BY_TYPE 保持 L1_L2；ORG_VISIBILITY 保持 L2_ONLY 60s。
 * YAML 运维覆盖使有效 L2 TTL 超过 10s 时启动失败。
 * 安全预算：上游授权 L2(10s) + Gateway 回源截止(5s) + Gateway L1(15s) ≤ 30s
 * ——后两项上限由 gateway 模块 GatewayCacheBoundaryValidator 强制。
 * </p>
 */
class PermCacheCatalogBoundaryTest {

    @Test
    void snapshotCatalogs_shouldBeL2OnlyWith10sTtl() {
        CacheCatalogEntry<?>[] catalogs = {
            PermCacheCatalog.EFFECTIVE_ROLES,
            PermCacheCatalog.ROLE_PERM_SNAPSHOT,
            PermCacheCatalog.TYPE_VALUE,
            PermCacheCatalog.TYPE_CODE,
            PermCacheCatalog.CONDITION_RULES,
            PermCacheCatalog.ROLE_MUTEX_RULE
        };
        assertThat(catalogs).hasSize(6);
        for (CacheCatalogEntry<?> catalog : catalogs) {
            assertThat(catalog.getMode())
                .as("快照链路目录 %s 必须为 L2_ONLY（不创建授权 L1）", catalog.getCode())
                .isEqualTo(CacheMode.L2_ONLY);
            assertThat(catalog.getL2Ttl())
                .as("快照链路目录 %s 代码默认 TTL 必须为 10s", catalog.getCode())
                .isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Test
    void nonSnapshotCatalogs_shouldKeepOrdinaryModes() {
        assertThat(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE.getMode()).isEqualTo(CacheMode.L1_L2);
        assertThat(PermCacheCatalog.ORG_VISIBILITY.getMode()).isEqualTo(CacheMode.L2_ONLY);
        assertThat(PermCacheCatalog.ORG_VISIBILITY.getL2Ttl()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void validator_shouldPassWithDefaultProperties() {
        PermCacheBoundaryValidator validator = new PermCacheBoundaryValidator(new CacheProperties());
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void validator_shouldFailWhenYamlOverrideExceeds10s() {
        CacheProperties properties = new CacheProperties();
        CacheProperties.CatalogOverride override = new CacheProperties.CatalogOverride();
        override.setL2Ttl(Duration.ofSeconds(11));
        properties.getCatalogs().put("perm:effective-roles", override);

        PermCacheBoundaryValidator validator = new PermCacheBoundaryValidator(properties);
        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("perm:effective-roles");
    }

    @Test
    void validator_shouldFailWhenAnySnapshotCatalogOverridden() {
        CacheProperties properties = new CacheProperties();
        CacheProperties.CatalogOverride override = new CacheProperties.CatalogOverride();
        override.setL2Ttl(Duration.ofSeconds(30));
        properties.getCatalogs().put("perm:role-perm-snapshot", override);

        PermCacheBoundaryValidator validator = new PermCacheBoundaryValidator(properties);
        assertThatThrownBy(validator::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("perm:role-perm-snapshot");
    }

    @Test
    void safetyBudget_shouldStayWithin30Seconds() {
        // 上游授权 L2 10s + Gateway 回源截止 5s + Gateway L1 15s ≤ 30s（第一阶段固定）
        Duration budget = PermCacheBoundaryValidator.MAX_SNAPSHOT_L2_TTL
            .plus(Duration.ofSeconds(5))
            .plus(Duration.ofSeconds(15));
        assertThat(budget).isEqualTo(Duration.ofSeconds(30));
    }
}
