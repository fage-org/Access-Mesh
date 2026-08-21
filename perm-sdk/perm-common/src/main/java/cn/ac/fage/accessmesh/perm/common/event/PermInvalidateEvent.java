package cn.ac.fage.accessmesh.perm.common.event;

import java.io.Serializable;
import java.util.Set;

/**
 * 权限失效广播事件
 * <p>
 * access-service 写路径事务提交后通过 Redis pub/sub（topic: {@code perm:invalidate}）广播。
 * Gateway 订阅后据此 evict 本地 INTERFACE_SNAPSHOT 缓存。
 * </p>
 * <p>
 * 设计（v3.5 §7.2 缓存一致性总线）：失败兜底——广播丢失不影响事务，TTL（30-60s）自然过期最终一致。
 * </p>
 *
 * @param tenantId     租户ID（IR-1.4 多租户硬隔离，广播按租户分区）
 * @param roleIds      受影响角色ID集合（角色权限变更）
 * @param userIds      受影响用户ID集合（用户角色关系变更）
 * @param serviceCodes 受影响服务编码集合（API mapping / 资源 / syncInterfaces 变更 → Gateway 本地快照陈旧）
 */
public record PermInvalidateEvent(Long tenantId, Set<Long> roleIds, Set<Long> userIds, Set<String> serviceCodes)
    implements Serializable {
}
