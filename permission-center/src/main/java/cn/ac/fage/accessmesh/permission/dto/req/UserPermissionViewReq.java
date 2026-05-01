package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Get user permissions view.
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
