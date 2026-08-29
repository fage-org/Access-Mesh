package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.vo.ConditionEvaluationDetail;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.List;
import java.util.Map;

/**
 * 权限条件领域服务接口
 * <p>
 * 提供权限条件的评估功能。权限条件定义了权限生效的附加约束规则，
 * 如时间范围、数据属性等。条件规则存储为JSON格式，支持复杂的条件表达式。
 * 评估时根据上下文参数判断每个权限条目是否满足条件约束。
 * </p>
 */
public interface PermissionConditionDomainService {

    /**
     * 评估权限条件
     * <p>
     * 对权限条目列表进行条件评估，过滤不符合条件的条目。
     * 根据上下文参数（如当前时间、用户属性等）判断条件是否满足。
     * 返回通过条件检查的权限条目列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param entries  待评估的权限条目列表
     * @param context  评估上下文，包含条件判断所需的参数
     * @return 通过条件检查的权限条目列表
     */
    List<RolePermEntry> evaluate(Long tenantId, List<RolePermEntry> entries,
                                                   Map<String, Object> context);

    /**
     * 逐项评估权限条件并返回评估明细（T-PERM-033 explain DTO 扩展）。
     * <p>
     * 与 {@link #evaluate} 使用同一套条件加载（缓存优先）与逐项评估逻辑，
     * 但不丢弃条目，而是返回每个挂条件条目的逐项评估过程：
     * 条件加载状态（OK/DISABLED/NOT_FOUND/INVALID）、logic、每项类型/脱敏参数/是否满足、
     * 整体是否通过。仅处理 {@code conditionId != null && hasCondition} 的条目，
     * 其余条目不产生明细（无条件即无评估过程）。
     * 日期/时间类条件按服务进程系统时钟评估（与运行时判定一致，不可模拟）；
     * IP 类条件按 context 中的 clientIp 评估。
     * </p>
     *
     * @param tenantId 租户ID
     * @param entries  待评估的权限条目列表（条件过滤前的候选命中条目）
     * @param context  评估上下文（clientIp）
     * @return 挂条件条目的评估明细列表（与入参条目顺序一致，无条件条目跳过）
     */
    List<ConditionEvaluationDetail> evaluateDetailed(Long tenantId, List<RolePermEntry> entries,
                                                     Map<String, Object> context);
}