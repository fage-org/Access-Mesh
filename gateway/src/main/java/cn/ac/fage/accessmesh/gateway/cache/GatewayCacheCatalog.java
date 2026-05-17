package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;

/**
 * Gateway 缓存目录
 * <p>
 * 定义 gateway 模块的所有缓存条目。
 * Gateway 使用 L1_ONLY 模式，无 Redis 依赖。
 * </p>
 */
public final class GatewayCacheCatalog {

    private GatewayCacheCatalog() {
    }

    /**
     * 权限检查结果缓存
     * <p>
     * Key: userId + serviceCode + apiPath（复合键）
     * Value: Boolean 权限检查结果
     * </p>
     * <p>
     * 使用 L1_ONLY 模式，Gateway 本地短 TTL 缓存。
     * deny decision 不缓存（fail-close）。
     * </p>
     */
    public static final CacheCatalogEntry<Boolean> PERM_CHECK =
        CacheCatalogEntry.<Boolean>builder()
            .code("gw:perm-check")
            .mode(CacheMode.L1_ONLY)
            .l1TtlMinutes(1)
            .l1MaxSize(5000)
            .valueType(new TypeRef<Boolean>() {})
            .build();
}