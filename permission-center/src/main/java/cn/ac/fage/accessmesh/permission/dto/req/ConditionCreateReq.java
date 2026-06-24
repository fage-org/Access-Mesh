package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限条件创建请求体
 * <p>
 * 用于创建新的权限条件，包括编码、名称、规则配置等。
 * </p>
 *
 * @param code             条件编码，必填，唯一标识
 * @param name             条件名称，必填，用于显示
 * @param conditionRules   条件规则JSON，必填，定义评估逻辑
 * @param enabled          是否启用，可选，默认true
 * @param gatewayEvaluable 是否可下发 Gateway 评估（T-PERM-017），可选，默认 false。
 *                         true 时规则随接口快照内联到 Gateway 本地重评；当前白名单见
 *                         {@code ConditionEvalUtils#GATEWAY_PUSHABLE_TYPES}
 *                         （IP_WHITELIST / IP_BLACKLIST / DATE_RANGE / TIME_RANGE）
 * @param description      条件描述，可选
 */
public record ConditionCreateReq(
    @NotBlank String code,
    @NotBlank String name,
    @NotBlank String conditionRules,
    Boolean enabled,
    Boolean gatewayEvaluable,
    String description
) {}
