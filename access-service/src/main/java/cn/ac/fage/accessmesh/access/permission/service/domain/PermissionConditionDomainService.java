package cn.ac.fage.accessmesh.access.permission.service.domain;

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
     * 条件规则写入口径校验（T-PERM-048 收敛为双轨共享：管理页 create/update 与授权内联轨同源）。
     * <p>
     * ① JSON 语法合法性（JsonValidationUtils）；② {@code gatewayEvaluable=true} 时
     * items[].type 全部在 {@code ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES} 白名单内
     * （T-PERM-017 C2.5，含未知类型默认 fail-close）。不通过抛 {@code BizException}
     * （20030 CONDITION_RULES_INVALID 系）。
     * </p>
     *
     * @param conditionRules     条件规则 JSON 字符串
     * @param gatewayEvaluable   最终生效的可下发标志（调用方先合并请求缺省值）
     */
    void assertConditionRulesValid(String conditionRules, boolean gatewayEvaluable);

}
