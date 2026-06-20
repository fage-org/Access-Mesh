package cn.ac.fage.accessmesh.gateway.config;

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
 * user→InterfaceSnapshotResp（用户在某服务下的全量接口权限快照）。鉴权降为本地内存匹配。
 * </p>
 * <p>
 * 缓存失效：TTL 兜底（30-60s）+ Redis pub/sub 主动广播（T-PERM-006 实现订阅器后 evict）。
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
     * 鉴权时按本地匹配器查询快照内 allowedApis，命中即放行，未命中回源拉取。
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
}
