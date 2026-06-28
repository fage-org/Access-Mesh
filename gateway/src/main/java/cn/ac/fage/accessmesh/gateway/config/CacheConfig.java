package cn.ac.fage.accessmesh.gateway.config;

import cn.ac.fage.accessmesh.gateway.cache.StaleEntry;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 网关Caffeine本地缓存配置类
 * <p>
 * T-PERM-001 快照模式：缓存 key 从 (user,service,method,path)→Boolean 改为
 * (tenantId,subjectTypeCode,userId,serviceCode)→InterfaceSnapshotResp（用户在某服务下的全量接口权限快照）。
 * 鉴权降为本地内存匹配 ({@code InterfaceSnapshotMatcher})。
 * </p>
 * <p>
 * 缓存失效（T-PERM-006 / T-PERM-018）：
 * <ul>
 *   <li><strong>TTL 兜底</strong>：通过 {@link GatewayProperties.Cache.L1#getTtlSeconds()} 配置，
 *       默认 30-60s，保证最终一致</li>
 *   <li><strong>Redis 广播主动失效</strong>：permission-center 写路径事务提交后，通过
 *       {@code PermissionChangeAspect} 发布 {@code PermInvalidateEvent} 到 topic {@code perm:invalidate}；
 *       Gateway 订阅器（T-PERM-006 实现）接收后 {@code evict} 本缓存——
 *       按 (tenantId, userIds, serviceCodes) 三维度精确失效</li>
 * </ul>
 * </p>
 * <p>
 * T-PERM-017 C4：含条件 entry 不再走单纯本地匹配——{@code InterfaceSnapshotMatcher.match} 返回三态
 * （ALLOW / FALLBACK / DENY），FALLBACK 分支由 {@code PermissionFilter} 调
 * {@code /perm/check-interface} 走实时鉴权。本缓存仍只缓存快照不缓存评估结果。
 * </p>
 */
@Configuration
public class CacheConfig {

    private final GatewayProperties gatewayProperties;

    /**
     * 构造缓存配置
     *
     * @param gatewayProperties 网关配置属性
     */
    public CacheConfig(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    /**
     * 创建接口权限快照缓存实例
     * <p>
     * 缓存维度：{@code (tenantId, subjectTypeCode, userId, serviceCode) → InterfaceSnapshotResp}。
     * 鉴权时按 {@code InterfaceSnapshotMatcher.match} 查询快照内 allowedApis：
     * <ul>
     *   <li>ALLOW → 直接放行</li>
     *   <li>FALLBACK → 调 check-interface 实时鉴权（含条件但 conditionRules 未下发）</li>
     *   <li>DENY → 403</li>
     * </ul>
     * 未命中时回源拉取并填充本缓存。
     * </p>
     *
     * @return Caffeine缓存实例
     */
    @Bean
    public Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache() {
        GatewayProperties.Cache.L1 l1 = gatewayProperties.getCache().getL1();
        return Caffeine.newBuilder()
            .maximumSize(l1.getMaxSize())
            .expireAfterWrite(l1.getTtlSeconds(), TimeUnit.SECONDS)
            .recordStats()
            .build();
    }

    /**
     * 创建陈旧接口权限快照缓存实例。
     * <p>
     * 回源成功时同步写入本缓存，后续 T-GW-003 的 stale-allow 分支可在主缓存自然过期、
     * permission-center 不可达时从这里取快照续命。显式失效事件会同步驱逐本缓存。
     * </p>
     */
    @Bean
    public Cache<String, StaleEntry> staleSnapshotCache() {
        GatewayProperties.Cache.L1 l1 = gatewayProperties.getCache().getL1();
        return Caffeine.newBuilder()
            .maximumSize(l1.getMaxSize())
            .expireAfterWrite(l1.getTtlSeconds() + l1.getStaleGraceSeconds(), TimeUnit.SECONDS)
            .recordStats()
            .build();
    }
}
