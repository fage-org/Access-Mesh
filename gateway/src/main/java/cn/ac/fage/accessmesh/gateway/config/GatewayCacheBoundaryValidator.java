package cn.ac.fage.accessmesh.gateway.config;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.gateway.cache.GatewayCacheCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Gateway 缓存安全边界启动校验器（T-ACCESS-008）
 * <p>
 * 强制校验两项预算上限，任一超限启动失败：
 * <ul>
 *   <li>权限快照 L1 有效 TTL（catalog 默认经 {@code accessmesh.cache.catalogs."gw:interface-snapshot".l1-ttl}
 *       运维覆盖后的最终值）≤ 15 秒</li>
 *   <li>快照加载全链路墙钟硬截止 {@code gateway.permission.snapshot-load-deadline} ≤ 5 秒</li>
 * </ul>
 * 与上游授权 L2 10s 共同构成「10+5+15 ≤ 30s」最坏陈旧窗口安全边界；第一阶段固定 10/5/15。
 * </p>
 */
@Component
public class GatewayCacheBoundaryValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(GatewayCacheBoundaryValidator.class);

    /**
     * Gateway 权限快照 L1 TTL 上限（第一阶段固定 15 秒）。
     */
    public static final Duration MAX_SNAPSHOT_L1_TTL = Duration.ofSeconds(15);

    /**
     * 快照加载全链路截止时间上限（第一阶段固定 5 秒）。
     */
    public static final Duration MAX_SNAPSHOT_LOAD_DEADLINE = Duration.ofSeconds(5);

    private final CacheProperties cacheProperties;
    private final GatewayProperties gatewayProperties;

    public GatewayCacheBoundaryValidator(CacheProperties cacheProperties,
                                         GatewayProperties gatewayProperties) {
        this.cacheProperties = cacheProperties;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Duration effectiveL1Ttl = cacheProperties.getEffectiveL1Ttl(
            GatewayCacheCatalog.INTERFACE_SNAPSHOT.getCode(),
            GatewayCacheCatalog.INTERFACE_SNAPSHOT.getL1Ttl());
        if (effectiveL1Ttl == null || effectiveL1Ttl.isNegative()
            || effectiveL1Ttl.compareTo(MAX_SNAPSHOT_L1_TTL) > 0) {
            throw new IllegalStateException(
                "Gateway 权限快照缓存有效 L1 TTL 超过安全边界上限 " + MAX_SNAPSHOT_L1_TTL
                    + "（当前 " + effectiveL1Ttl + "）；快照安全预算为 上游L2(10s)+回源截止(5s)+网关L1(15s)≤30s");
        }

        Duration deadline = gatewayProperties.getPermission().getSnapshotLoadDeadline();
        if (deadline == null || deadline.isNegative() || deadline.isZero()
            || deadline.compareTo(MAX_SNAPSHOT_LOAD_DEADLINE) > 0) {
            throw new IllegalStateException(
                "gateway.permission.snapshot-load-deadline 必须为不超过 " + MAX_SNAPSHOT_LOAD_DEADLINE
                    + " 的正时长（当前 " + deadline + "）");
        }

        log.info("Gateway cache boundary validated: snapshot L1 TTL={}, load deadline={}",
            effectiveL1Ttl, deadline);
    }
}
