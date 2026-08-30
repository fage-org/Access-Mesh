package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 权限条件更新请求体
 * <p>
 * 用于更新权限条件的信息，包括名称、规则配置、启用状态等。
 * 以业务键 code 定位（uk tenant+code，T-PERM-029 从内部主键 conditionId 切换），
 * code 本身不可更新。
 * </p>
 *
 * @param code             条件编码，必填，定位键（创建后不可改，最长64字符）
 * @param name             条件名称，可选
 * @param conditionRules   条件规则JSON，可选
 * @param enabled          是否启用，可选
 * @param gatewayEvaluable 是否可下发 Gateway 评估（T-PERM-017），可选；
 *                         切换该字段将触发 Gateway 接口快照缓存失效
 * @param description      条件描述，可选
 */
public record ConditionUpdateReq(
    @NotBlank @Size(max = 64) String code,
    @Size(max = 128) String name,
    String conditionRules,
    Boolean enabled,
    Boolean gatewayEvaluable,
    @Size(max = 512) String description
) {}
