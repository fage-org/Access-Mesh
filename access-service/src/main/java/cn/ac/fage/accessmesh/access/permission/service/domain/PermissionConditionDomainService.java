package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 条件规则写入口径校验（T-PERM-048 收敛为双轨共享：管理页 create/update 与授权内联轨同源）。
     * <p>
     * ① JSON 语法合法性（JsonValidationUtils）；② {@code gatewayEvaluable=true} 时
     * items[].type 全部在 {@code ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES} 白名单内
     * （T-PERM-017 C2.5，含未知类型默认 fail-close）。不通过抛 {@code BizException}
     * （20030 CONDITION_RULES_INVALID 系）。
     * </p>
     *
     * @param conditionRules   条件规则 JSON 字符串
     * @param gatewayEvaluable 最终生效的可下发标志（调用方先合并请求缺省值）
     */
    void assertConditionRulesValid(String conditionRules, boolean gatewayEvaluable);

    /**
     * 创建授权页内联条件（T-PERM-048 定案①，apply-grant-plan 内联轨同事务调用）。
     * <p>
     * source=INLINE、code 自动生成（{@code inline-} + UUID，租户内碰撞概率可忽略、
     * uk 兜底随事务失败）、enabled 恒 true；规则写入口径与管理页轨同源校验。
     * 事务由调用方（PermissionGrantAppService 单事务）声明。1:1 属于其授权记录——
     * 门禁随授权入口 ROLE:MANAGE 携带（定案②），本方法不做 CONDITION 写门禁。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operatorId 操作者ID（授权链路主体）
     * @param def        内联条件定义
     * @return 已落库的内联条件实体（含生成 code 与自增 id）
     */
    PermissionCondition createInlineCondition(Long tenantId, Long operatorId,
        ApplyGrantPlanReq.InlineConditionDef def);

    /**
     * 就地编辑既有内联条件的规则（授权页「只能在授权页更改」的写入口，T-PERM-048 定案①）。
     * <p>
     * 仅接受 source=INLINE 的行（MANAGED 条件须走管理页 update）；enabled 维持 true 不变，
     * 可变面=name/conditionRules/gatewayEvaluable（最终态联合校验同管理页轨）。
     * 编辑后由本方法登记 markConditions（CONDITION_RULES 缓存失效；Gateway 快照面由
     * 授权链路 markRoles 的租户级安全清理覆盖，T-PERM-006）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operatorId 操作者ID
     * @param condition  既有内联条件实体（调用方已加载）
     * @param def        内联条件定义（覆盖 name/rules/gatewayEvaluable）
     * @throws cn.ac.fage.accessmesh.common.exception.BizException 条件非 INLINE 来源（20060）
     */
    void editInlineCondition(Long tenantId, Long operatorId, PermissionCondition condition,
        ApplyGrantPlanReq.InlineConditionDef def);

    /**
     * 回收引用归零的内联条件（授权行删除/换绑后同事务调用，T-PERM-048 定案①回收轨）。
     * <p>
     * 候选集中 source=INLINE 且已无有效授权行引用（selectReferencedConditionIds）的行
     * 批量软删（INLINE 无投影行，回收面收敛为条件行本身）并 markConditions；
     * 仍被引用（跨角色显式引用被 20060 引用轨焊死，理论不可达，防御保留）的跳过不删。
     * </p>
     *
     * @param tenantId     租户ID
     * @param candidateIds 受影响授权行的原 conditionId 候选集合
     * @return 实际回收的条件 id 集合（空=无可回收）
     */
    Set<Long> recycleOrphanInlineConditions(Long tenantId, Set<Long> candidateIds);

}
