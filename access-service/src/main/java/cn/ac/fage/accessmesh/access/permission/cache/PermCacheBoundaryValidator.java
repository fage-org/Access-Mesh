package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 授权缓存安全边界启动校验器（T-ACCESS-008）
 * <p>
 * 强制校验「上游授权 L2 TTL ≤ 10 秒」：所有可能影响接口权限快照的 catalog
 * 必须为 L2_ONLY 且有效 L2 TTL（catalog 代码默认值经 {@code accessmesh.cache.catalogs.*}
 * 运维覆盖后的最终值）不超过 10 秒。任一超限或模式违规在启动阶段抛出异常，
 * 上下文启动失败——不允许带违规配置运行。
 * </p>
 * <p>
 * 与 Gateway 侧（快照 L1 ≤15s、回源截止 ≤5s）共同构成「10+5+15 ≤ 30s」
 * 最坏陈旧窗口安全边界；第一阶段固定 10s/5s/15s。
 * </p>
 */
@Component
public class PermCacheBoundaryValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PermCacheBoundaryValidator.class);

    /**
     * 快照链路 catalog 有效 L2 TTL 上限（第一阶段固定 10 秒）。
     */
    public static final Duration MAX_SNAPSHOT_L2_TTL = Duration.ofSeconds(10);

    /**
     * 可能影响接口权限快照的 catalog（T-ACCESS-008 用户决策 2026-08-21：快照链路 6 个；
     * OPERATION_PERMISSIONS_BY_TYPE 不进快照内容，保持 L1_L2 普通缓存）。
     */
    private static final Set<CacheCatalogEntry<?>> SNAPSHOT_CATALOGS = Set.of(
        PermCacheCatalog.EFFECTIVE_ROLES,
        PermCacheCatalog.ROLE_PERM_SNAPSHOT,
        PermCacheCatalog.TYPE_VALUE,
        PermCacheCatalog.TYPE_CODE,
        PermCacheCatalog.CONDITION_RULES,
        PermCacheCatalog.ROLE_MUTEX_RULE
    );

    private final CacheProperties cacheProperties;

    public PermCacheBoundaryValidator(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = SNAPSHOT_CATALOGS.stream()
            .filter(catalog -> !isValid(catalog))
            .map(catalog -> describeViolation(catalog))
            .toList();
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "授权缓存安全边界校验失败（快照链路 catalog 必须 L2_ONLY 且有效 L2 TTL≤"
                    + MAX_SNAPSHOT_L2_TTL.toSeconds() + "s）: " + String.join("; ", violations));
        }
        log.info("Perm cache boundary validated: {} snapshot catalogs are L2_ONLY with effective L2 TTL <= {}s",
            SNAPSHOT_CATALOGS.size(), MAX_SNAPSHOT_L2_TTL.toSeconds());
    }

    private boolean isValid(CacheCatalogEntry<?> catalog) {
        if (catalog.getMode() != CacheMode.L2_ONLY) {
            return false;
        }
        Duration effective = cacheProperties.getEffectiveL2Ttl(catalog.getCode(), catalog.getL2Ttl());
        return effective != null
            && !effective.isNegative()
            && effective.compareTo(MAX_SNAPSHOT_L2_TTL) <= 0;
    }

    private String describeViolation(CacheCatalogEntry<?> catalog) {
        Duration effective = cacheProperties.getEffectiveL2Ttl(catalog.getCode(), catalog.getL2Ttl());
        return catalog.getCode() + " (mode=" + catalog.getMode()
            + ", effectiveL2Ttl=" + (effective == null ? "null" : effective) + ")";
    }
}
