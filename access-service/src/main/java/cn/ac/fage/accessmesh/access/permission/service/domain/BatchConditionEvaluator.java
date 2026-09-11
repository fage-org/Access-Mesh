package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请求级批量条件评估器（T-PERM-061 A+ 形态——条件快照 = 请求级增量、四态建模）。
 * <p>
 * 由 {@link PermissionConditionDomainService#openBatchEvaluator} 创建，per-request 实例
 * 经方法参数传递（禁止落在单例字段），批量路径各段（scopeAll / 实例 / 父判定）共用：
 * </p>
 * <ul>
 *   <li>快照形态 {@code conditionId → LoadedRules 四态（OK / NOT_FOUND / DISABLED / INVALID）}；
 *       仅 enabled=true 且解析成功的规则作为 OK 写入 CONDITION_RULES 正缓存（putBatch 带
 *       beginRead 剩余 TTL）——selectValidByIds 只滤租户+软删不滤 enabled，朴素批量把禁用
 *       条件当有效规则入缓存 = 权限绕过方向；</li>
 *   <li>失败态（缺失/禁用/解析失败）记录请求级状态，评估 fail-close（与单条 loadRules 语义一致）；</li>
 *   <li>增量装载：{@link #preload} 只批量加载快照中尚未出现的 conditionId（每阶段至多一批次
 *       getBatch + miss 回源；同 ID 请求内至多回源一次）——分段化与「预取 ×1」存在数据依赖环
 *       （scopeAll 放行判定需要条件、实例行条件 ID 要实例装载后才知、父判定条件 ID 要父查询
 *       后才知），严格 ×1 不可达，验收口径 = 每阶段至多一次 + 同 ID 至多回源一次。</li>
 * </ul>
 */
public interface BatchConditionEvaluator {

    /**
     * 增量预载条件快照（四态）。
     * <p>
     * 已在快照中的 ID 跳过（含失败态——同 ID 请求内至多回源一次）；空集无操作。
     * 引擎应在每段行集到手后对条件 ID 并集调用一次，随后段内评估零 IO。
     * </p>
     *
     * @param tenantId     租户ID
     * @param conditionIds 本段行集引用的条件 ID 并集
     */
    void preload(Long tenantId, Set<Long> conditionIds);

    /**
     * 评估权限条目的条件（消费请求级快照，fail-close）。
     * <p>
     * 与单条 {@link PermissionConditionDomainService#evaluate} 语义一致：无条件条目放行、
     * 快照非 OK（NOT_FOUND / DISABLED / INVALID）的条目拒绝；防御性对未预载 ID 兜底装载
     * （引擎按阶段正确预载时零 IO）。同条件在请求内的评估结果记忆复用（评估上下文请求级唯一）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param entries  待评估的权限条目列表
     * @param context  评估上下文（请求级唯一）
     * @return 通过条件检查的权限条目列表
     */
    List<RolePermEntry> evaluate(Long tenantId, List<RolePermEntry> entries, Map<String, Object> context);
}
