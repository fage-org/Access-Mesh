package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Shared: get user permissions view.
 */
public record UserPermissionViewReq(
    @NotBlank String targetType,
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
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
