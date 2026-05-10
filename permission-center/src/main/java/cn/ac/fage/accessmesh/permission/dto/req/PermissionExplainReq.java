package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限解释查询请求体
 * <p>
 * 用于查询权限判定的详细解释，包括权限来源和最近变更。
 * 支持用户和角色两种目标类型。
 * </p>
 *
 * @param targetType         目标类型，必填（USER/ROLE）
 * @param subjectTypeCode    用户类型编码，目标类型为USER时必填
 * @param subjectExternalId  用户外部标识，目标类型为USER时必填
 * @param roleTypeCode       角色类型编码，目标类型为ROLE时必填
 * @param roleExternalId     角色外部标识，目标类型为ROLE时必填
 * @param domainCode         业务域编码，可选
 * @param resourceTypeCode   资源类型编码，必填
 * @param resourceCode       资源编码，必填
 * @param codeType           编码类型，可选
 * @param operationCode      操作编码，必填
 * @param includeSourceRoles 是否包含来源角色，可选
 * @param includeRecentChanges 是否包含最近变更，可选
 * @param recentDays         最近变更天数，可选
 */
public record PermissionExplainReq(
    @NotBlank String targetType,
    String subjectTypeCode,
    String subjectExternalId,
    String roleTypeCode,
    String roleExternalId,
    String domainCode,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,
    String codeType,
    @NotBlank String operationCode,
    Boolean includeSourceRoles,
    Boolean includeRecentChanges,
    Integer recentDays
) {}