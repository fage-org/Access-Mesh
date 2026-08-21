package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;

import java.time.Duration;

/**
 * Gateway 缓存目录
 * <p>
 * 定义 gateway 模块的所有缓存条目。Gateway 使用独立部署的自身唯一
 * {@code CacheService}（L1_ONLY，无 Redis 依赖），保留 {@code gw:} 前缀，
 * 不并入 access-service 的目录命名空间。
 * </p>
 * <p>
 * T-ACCESS-008（用户决策：配置统一到 catalog + accessmesh.cache 运维覆盖）：
 * TTL/容量唯一来源为本目录声明，运维覆盖走
 * {@code accessmesh.cache.catalogs."[gw:interface-snapshot]".l1-ttl / l1-maximum-size}；
 * 有效 L1 TTL&gt;15s 时启动失败（{@code GatewayCacheBoundaryValidator}），
 * 与上游授权 L2 10s、快照回源截止 5s 构成「10+5+15≤30s」安全边界。
 * </p>
 */
public final class GatewayCacheCatalog {

    private GatewayCacheCatalog() {
    }

    /**
     * 接口权限快照缓存（T-PERM-001 快照模式 / T-ACCESS-008 迁移 CacheService）
     * <p>
     * Key: identifier = {@code subjectTypeCode:userId:serviceCode}
     * （CacheService 组装完整键 {@code {tenantId}:gw:interface-snapshot:{identifier}}）
     * Value: {@link InterfaceSnapshotResp} 用户在该服务下的全量接口权限快照
     * </p>
     * <p>
     * L1_ONLY 本地 Caffeine；TTL 15s 为安全边界上限（快照失效主靠 Redis pub/sub
     * 主动广播 + 订阅重连全量清空，TTL 兜底）。鉴权时按
     * {@code InterfaceSnapshotMatcher} 本地匹配 allowedApis。
     * </p>
     */
    public static final CacheCatalogEntry<InterfaceSnapshotResp> INTERFACE_SNAPSHOT =
        CacheCatalogEntry.<InterfaceSnapshotResp>builder()
            .code("gw:interface-snapshot")
            .mode(CacheMode.L1_ONLY)
            .l1Ttl(Duration.ofSeconds(15))
            .l1MaxSize(50000)
            .valueType(new TypeRef<InterfaceSnapshotResp>() {})
            .build();
}
