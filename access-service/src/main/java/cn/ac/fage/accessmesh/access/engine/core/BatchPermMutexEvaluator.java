package cn.ac.fage.accessmesh.access.engine.core;

import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;

import java.util.List;
import java.util.Set;

/**
 * 请求级批量权限互斥评估器（T-PERM-061 A+ 形态——计算与通知解耦 + 静态数据共享装载）。
 * <p>
 * 由 {@link PermissionConflictDomainService#openBatchMutexEvaluator} 创建，per-request 实例
 * 经方法参数传递（禁止落在单例字段）。计算与通知解耦（b2 定案；单条通知支线
 * {@code filterPermMutex}/{@code computePermMutex} 已随 T-PERM-092 裁剪删除，本评估器为
 * PERM_MUTEX 剔除语义唯一实现）：
 * </p>
 * <ul>
 *   <li><b>只共享装载，不共享计算</b>：PERM_MUTEX 规则请求级装载一次；操作索引按 distinct
 *       类型 O(K) 惰性扩——计算仍按传入条目集合作（集合语义：对子集整体算 opIds、
 *       规则两端同场才冲突且两端全丢，合并评估必不等价）；</li>
 *   <li><b>计算不通知</b>：{@link #compute} 仅返回过滤结果与命中规则 ID——互斥命中审计由
 *       新引擎按 ConflictEvidence 受控提交（T-PERM-088，execution＋item＋stage＋ruleRef 聚合；
 *       旧 (组, ruleId) ledger 聚合通知口随旧执行体 T-PERM-092 删除）。</li>
 * </ul>
 */
public interface BatchPermMutexEvaluator {

    /**
     * 计算权限互斥过滤（不通知）。
     * <p>
     * 剔除语义：条目 grantedBits 经 (resourceType, binaryBit) 精确查表解析操作
     * （无精确匹配的复合位行被静默排除出互斥判定）；规则两端都在条目操作 ID 集合中时
     * 两端全部剔除。空规则短路（I01，
     * T-PERM-095）：规则装载为空时直接返回原条目，操作目录零装载。
     * </p>
     *
     * @param entries 待互斥计算的权限条目列表
     * @return 过滤结果 + 命中规则 ID 集合（空集 = 无冲突）
     */
    PermMutexComputation compute(List<RolePermEntry> entries);

    /**
     * 按 ruleId 返回规则操作端点（新核心证据收集用，T-PERM-088）。
     * <p>
     * 消费评估器请求级已装载的规则数据，零额外 I/O；未装载（未计算过）或未知的
     * ruleId 不返回条目——证据收集仅在存在命中时调用，此时规则必已装载。
     * </p>
     *
     * @param ruleIds 命中的互斥规则 ID 集合
     * @return 规则引用列表（ruleId＋两端操作权限 ID）
     */
    List<MutexRuleRef> describeRules(Set<Long> ruleIds);

    /** 互斥规则引用（ruleId＋两端操作权限 ID；ConflictEvidence.actualConflictingOperationIds 来源）。 */
    record MutexRuleRef(long ruleId, long firstOperationPermissionId, long secondOperationPermissionId) {}

    /**
     * 互斥计算结果。
     *
     * @param filtered 剔除冲突条目后的列表
     * @param triggeredRuleIds 命中的互斥规则 ID 集合（两端都在场的 AND 判定）
     */
    record PermMutexComputation(List<RolePermEntry> filtered, Set<Long> triggeredRuleIds) {}

}
