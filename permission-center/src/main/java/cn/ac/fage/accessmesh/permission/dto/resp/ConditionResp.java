package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 权限条件响应体
 * <p>
 * 返回权限条件的详细信息，包括编码、名称、规则配置等。
 * 用于权限条件查询接口的响应。
 * </p>
 *
 * @param id               条件ID
 * @param tenantId         租户ID
 * @param code             条件编码，唯一标识
 * @param name             条件名称，用于显示
 * @param conditionRules   条件规则JSON，定义评估逻辑
 * @param enabled          是否启用
 * @param gatewayEvaluable 是否可下发 Gateway 评估（T-PERM-017）。true 时规则随接口快照内联到 Gateway 本地重评
 * @param description      条件描述
 * @param createdAt        创建时间
 */
public record ConditionResp(
    Long id,
    Long tenantId,
    String code,
    String name,
    String conditionRules,
    Boolean enabled,
    Boolean gatewayEvaluable,
    String description,
    LocalDateTime createdAt
) {}
