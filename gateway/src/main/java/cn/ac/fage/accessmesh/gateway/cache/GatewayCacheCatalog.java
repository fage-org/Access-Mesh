package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;

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
 * {@code accessmesh.cache.catalogs."[gw:interface-admission-snapshot]".l1-ttl / l1-maximum-size}；
 * 有效 L1 TTL&gt;15s 时启动失败（{@code GatewayCacheBoundaryValidator}），
 * 与上游授权 L2 10s、快照回源截止 5s 构成「10+5+15≤30s」安全边界。
 * </p>
 */
public final class GatewayCacheCatalog {

    private GatewayCacheCatalog() {
    }

    /**
     * 操作准入快照缓存（T-ACCESS-059 无迁移期切换；旧 API:ACCESS 快照条目随切链退役）
     * <p>
     * Key: identifier = {@code subjectTypeCode:userId:serviceCode}
     * （CacheService 组装完整键 {@code {tenantId}:gw:interface-admission-snapshot:{identifier}}）
     * Value: {@link InterfaceAdmissionSnapshotResp} 该服务完整启用路由与主体候选分支投影。
     * 新 schema 与旧快照命名空间隔离（N22）；本地判定序=校验模式/版本/时效→完整路由
     * 匹配与歧义检测→唯一要求→评条件分支（无通过分支回源在线判定）
     * </p>
     * <p>
     * L1_ONLY 本地 Caffeine；TTL 15s 为丢失广播兜底上限（快照时效主门禁=服务端 expiresAt，
     * T-ACCESS-060 边界推导收口：最坏陈旧＝access 事实族 L2≤10s＋构建耗时≤5s（回源截止
     * 约束——generatedAt 在构建完成后计算，构建期事实年龄继续增长）＋快照有效期 15s
     * ＝30s＝30s 目标压线达标（0 余量），
     * 方程由本侧 L1≤15s＋回源截止≤5s 与 access 侧上游 L2＋快照有效期≤30s 双层启动校验锁定）。
     * 快照失效主靠 Redis pub/sub 主动广播 + 订阅重连全量清空，TTL 兜底。
     * </p>
     */
    public static final CacheCatalogEntry<InterfaceAdmissionSnapshotResp> INTERFACE_ADMISSION_SNAPSHOT =
        CacheCatalogEntry.<InterfaceAdmissionSnapshotResp>builder()
            .code("gw:interface-admission-snapshot")
            .mode(CacheMode.L1_ONLY)
            .l1Ttl(Duration.ofSeconds(15))
            .l1MaxSize(50000)
            .valueType(new TypeRef<InterfaceAdmissionSnapshotResp>() {})
            .build();
}
