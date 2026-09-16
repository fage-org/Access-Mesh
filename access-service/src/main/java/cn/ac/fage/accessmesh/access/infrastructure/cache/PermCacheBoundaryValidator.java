package cn.ac.fage.accessmesh.access.infrastructure.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        AccessCacheCatalog.EFFECTIVE_ROLES,
        AccessCacheCatalog.ROLE_PERM_SNAPSHOT,
        AccessCacheCatalog.TYPE_VALUE,
        AccessCacheCatalog.TYPE_CODE,
        AccessCacheCatalog.CONDITION_RULES,
        AccessCacheCatalog.ROLE_MUTEX_RULE
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
        // Q-006（T-ACCESS-048）：覆盖键按 catalog code 精确匹配，未知 code 的覆盖静默不生效——
        // 最典型为 T-ACCESS-039 改名后 Nacos 残留的 admin:org-visibility 覆盖键（TTL 沿用代码默认值）。
        // 只告警不 fail-fast（覆盖丢失非安全事件，静默才是要暴露的问题）。
        Set<String> unknownKeys = detectUnknownOverrideKeys();
        if (!unknownKeys.isEmpty()) {
            log.warn("accessmesh.cache.catalogs 存在未知 catalog code 的覆盖键（将静默不生效）: {} ——已知 code: {}；"
                    + "若为 T-ACCESS-039 改名前的 admin:org-visibility，请迁移至 access:org-visibility（Q-006）",
                unknownKeys, knownCatalogCodes());
        }
        log.info("Perm cache boundary validated: {} snapshot catalogs are L2_ONLY with effective L2 TTL <= {}s",
            SNAPSHOT_CATALOGS.size(), MAX_SNAPSHOT_L2_TTL.toSeconds());
    }

    /**
     * 找出配置了 TTL 覆盖但 catalog 册内不存在的 code（Q-006，T-ACCESS-048）。
     * 已知 code 集合经反射取 AccessCacheCatalog 全部 CacheCatalogEntry 公共常量——新增条目自动纳入；
     * evict-only 别名（ORG_VISIBILITY_LEGACY）显式排除：无读取路径、覆盖对其无意义，
     * 对旧 code 的残留覆盖键恰恰是本检查要暴露的对象。
     */
    Set<String> detectUnknownOverrideKeys() {
        Set<String> known = knownCatalogCodes();
        return cacheProperties.getCatalogs().keySet().stream()
            .filter(code -> !known.contains(code))
            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> knownCatalogCodes() {
        return Arrays.stream(AccessCacheCatalog.class.getFields())
            .filter(field -> field.getType() == CacheCatalogEntry.class)
            .map(this::readCatalogCode)
            .filter(code -> !code.equals(AccessCacheCatalog.ORG_VISIBILITY_LEGACY.getCode()))
            .collect(Collectors.toUnmodifiableSet());
    }

    private String readCatalogCode(Field field) {
        try {
            return ((CacheCatalogEntry<?>) field.get(null)).getCode();
        } catch (IllegalAccessException e) {
            // 公共常量字段不可达属于编程错误，直接失败
            throw new IllegalStateException("无法读取 AccessCacheCatalog 常量: " + field.getName(), e);
        }
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
