package cn.ac.fage.accessmesh.access.permission.dto.req;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 权限解释查询请求体
 * <p>
 * 用于查询权限判定的详细解释，包括权限来源和最近变更。
 * 支持用户和角色两种目标类型。
 * </p>
 *
 * @param targetType         目标类型，必填（USER/ROLE；非法值 400 早拒，服务层按 USER 分支 fail-closed）
 * @param subjectTypeCode    用户类型编码，目标类型为USER时必填
 * @param subjectExternalId  用户外部标识，目标类型为USER时必填
 * @param roleTypeCode       角色类型编码，目标类型为ROLE时必填
 * @param roleExternalId     角色外部标识，目标类型为ROLE时必填
 * @param domainCode         业务域编码，可选
 * @param resourceTypeCode   资源类型编码，必填
 * @param resourceCode       资源编码，scopeMode=INSTANCE 时必填
 * @param codeType           编码类型，scopeMode=INSTANCE 时必填
 * @param operationCode      操作编码，必填
 * @param scopeMode          范围模式，INSTANCE/ALL
 * @param includeSourceRoles 是否包含来源角色，可选
 * @param includeRecentChanges 是否包含最近变更，可选
 * @param recentDays         最近变更天数，可选
 * @param context            条件评估上下文（T-PERM-033），可选：管理员输入的模拟 clientIp；
 *                           未提供时回退当前请求环境（评估上下文来源在响应标注）。
 *                           日期/时间类条件按服务进程系统时钟评估，不可模拟。
 */
public record PermissionExplainReq(
    @NotBlank @Pattern(regexp = "USER|ROLE", message = "targetType 仅支持 USER/ROLE") String targetType,
    String subjectTypeCode,
    String subjectExternalId,
    String roleTypeCode,
    String roleExternalId,
    String domainCode,
    @NotBlank String resourceTypeCode,
    String resourceCode,
    String codeType,
    @NotBlank String operationCode,
    ScopeMode scopeMode,
    Boolean includeSourceRoles,
    Boolean includeRecentChanges,
    Integer recentDays,
    ExplainContext context
) {

    /**
     * explain 条件评估上下文（T-PERM-033）
     *
     * @param clientIp 模拟的客户端 IP，用于 IP 黑白名单条件评估；空白视为未提供
     */
    public record ExplainContext(
        String clientIp
    ) {}
}
