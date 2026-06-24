package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 权限条件更新请求体
 * <p>
 * 用于更新权限条件的信息，包括名称、规则配置、启用状态等。
 * </p>
 *
 * @param conditionId      条件ID，必填
 * @param name             条件名称，可选
 * @param conditionRules   条件规则JSON，可选
 * @param enabled          是否启用，可选
 * @param gatewayEvaluable 是否可下发 Gateway 评估（T-PERM-017），可选；
 *                         切换该字段将触发 Gateway 接口快照缓存失效
 * @param description      条件描述，可选
 */
public record ConditionUpdateReq(
    @NotNull Long conditionId,
    String name,
    String conditionRules,
    Boolean enabled,
    Boolean gatewayEvaluable,
    String description
) {}
