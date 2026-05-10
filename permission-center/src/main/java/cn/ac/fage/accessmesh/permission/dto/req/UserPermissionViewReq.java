package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 用户权限视图查询请求体
 * <p>
 * 用于查询用户的有效权限视图，支持按资源类型、操作和关键词过滤。
 * </p>
 *
 * @param targetType           目标类型，必填（USER/ROLE）
 * @param subjectTypeCode      用户类型编码，目标类型为USER时使用
 * @param subjectExternalId    用户外部标识，目标类型为USER时使用
 * @param domainCode           业务域编码，可选
 * @param roleTypeCode         角色类型编码，目标类型为ROLE时使用
 * @param roleExternalId       角色外部标识，目标类型为ROLE时使用
 * @param resourceTypeCodes    资源类型编码列表，可选
 * @param operationCodes       操作编码列表，可选
 * @param resourceKeyword      资源关键词，可选
 * @param sourceRoleExternalId 来源角色外部标识，可选
 * @param includeScopes        是否包含范围权限，可选
 * @param includeApiResources  是否包含API资源，可选
 * @param includeSourceRoles   是否包含来源角色，可选
 * @param sourceRoleLimit      来源角色显示限制，可选
 * @param pageNum              页码，可选
 * @param pageSize             每页条数，可选
 */
public record UserPermissionViewReq(
    @NotBlank String targetType,
    String subjectTypeCode,
    String subjectExternalId,
    String domainCode,
    String roleTypeCode,
    String roleExternalId,
    List<String> resourceTypeCodes,
    List<String> operationCodes,
    String resourceKeyword,
    String sourceRoleExternalId,
    Boolean includeScopes,
    Boolean includeApiResources,
    Boolean includeSourceRoles,
    Integer sourceRoleLimit,
    Integer pageNum,
    Integer pageSize
) {}