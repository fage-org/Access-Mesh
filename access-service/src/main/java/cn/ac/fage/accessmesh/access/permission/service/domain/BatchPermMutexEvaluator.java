package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.List;
import java.util.Set;

/**
 * 请求级批量权限互斥评估器（T-PERM-061 A+ 形态——计算与通知解耦 + 静态数据共享装载）。
 * <p>
 * 由 {@link PermissionConflictDomainService#openBatchMutexEvaluator} 创建，per-request 实例
 * 经方法参数传递（禁止落在单例字段）。与单条 {@code filterPermMutex}（检出冲突即通知、
 * detail 用任一端点命中 OR 过滤）的差异为设计定稿（b2 定案）：
 * </p>
 * <ul>
 *   <li><b>只共享装载，不共享计算</b>：PERM_MUTEX 规则请求级装载一次；操作索引按 distinct
 *       类型 O(K) 惰性扩——计算仍按传入条目集合作（集合语义：filterPermMutex 对子集整体算
 *       opIds、规则两端同场才冲突且两端全丢，合并评估必不等价）；</li>
 *   <li><b>计算不通知</b>：{@link #compute} 仅返回过滤结果与命中规则 ID，通知由调用方
 *       （引擎批量层）维护 {@code (组, ruleId) → 命中 originalIndex} ledger 聚合后经
 *       {@link #notifyHits} 显式触发——每 (组, ruleId) 一条审计行，detail 由实际命中规则
 *       （first/second 两端都在 opIds 的 AND 判定）构造并携 hitItemCount。</li>
 * </ul>
 */
public interface BatchPermMutexEvaluator {

    /**
     * 计算权限互斥过滤（不通知）。
     * <p>
     * 与单条 {@code filterPermMutex} 的剔除语义逐分支一致：条目 grantedBits 经
     * (resourceType, binaryBit) 精确查表解析操作（无精确匹配的复合位行被静默排除出
     * 互斥判定）；规则两端都在条目操作 ID 集合中时两端全部剔除。
     * </p>
     *
     * @param entries 待互斥计算的权限条目列表
     * @return 过滤结果 + 命中规则 ID 集合（空集 = 无冲突）
     */
    PermMutexComputation compute(List<RolePermEntry> entries);

    /**
     * 聚合通知互斥命中（ledger flush；每 hit 一条审计行）。
     * <p>
     * detail 由请求级规则数据按 AND 命中构造（ruleId → first/second 操作），未触发规则
     * 不出现。通知失败不抛出（对齐单条 notifyPermConflict 的容错边界）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param hits     聚合后的命中条目（(组, ruleId) 去重 + hitItemCount）
     */
    void notifyHits(Long tenantId, List<MutexHit> hits);

    /**
     * 互斥计算结果。
     *
     * @param filtered 剔除冲突条目后的列表
     * @param triggeredRuleIds 命中的互斥规则 ID 集合（两端都在场的 AND 判定）
     */
    record PermMutexComputation(List<RolePermEntry> filtered, Set<Long> triggeredRuleIds) {}

    /**
     * 聚合后的互斥命中（ledger 条目）。
     *
     * @param groupKey 组标识（分组键可读串，如 {@code INSTANCE:MENU:VIEW:...}）
     * @param ruleId 命中的互斥规则 ID
     * @param hitItemCount 命中 item 数（去重、段间合并）
     */
    record MutexHit(String groupKey, Long ruleId, int hitItemCount) {}
}
