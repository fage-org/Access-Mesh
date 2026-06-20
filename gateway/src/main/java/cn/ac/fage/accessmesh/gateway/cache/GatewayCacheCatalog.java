package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;

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
     * 接口权限快照缓存（T-PERM-001 快照模式）
     * <p>
     * Key: (tenantId, subjectTypeCode, userId, serviceCode) 复合键
     * Value: {@link InterfaceSnapshotResp} 用户在该服务下的全量接口权限快照
     * </p>
     * <p>
     * 使用 L1_ONLY 模式，Gateway 本地 Caffeine 缓存。TTL 兜底 30s，
     * 快照失效主要靠 Redis pub/sub 主动广播（T-PERM-006 实现订阅器后 evict）。
     * 鉴权时按 {@code InterfaceSnapshotMatcher} 本地匹配 allowedApis。
     * </p>
     * <p>
     * 注：实际 TTL/maxSize 由 {@code GatewayProperties.Cache.L1} 注入 CacheConfig，
     * 本条目为目录声明（与 CacheConfig 配置保持一致）。
     * </p>
     */
    public static final CacheCatalogEntry<InterfaceSnapshotResp> INTERFACE_SNAPSHOT =
        CacheCatalogEntry.<InterfaceSnapshotResp>builder()
            .code("gw:interface-snapshot")
            .mode(CacheMode.L1_ONLY)
            .l1TtlMinutes(1)
            .l1MaxSize(50000)
            .valueType(new TypeRef<InterfaceSnapshotResp>() {})
            .build();
}
