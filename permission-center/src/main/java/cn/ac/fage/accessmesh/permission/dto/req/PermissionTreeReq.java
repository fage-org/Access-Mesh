package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.Set;

/**
 * Request for querying permission tree from a starting resource node.
 * Returns accessible resources in ancestor/descendant directions.
 */
public record PermissionTreeReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,          // Starting resource node
    String codeType,
    @NotEmpty Set<String> operationCodes,   // Operation types to check
    @Pattern(regexp = "ANCESTORS|DESCENDANTS|BOTH")
    @NotBlank String direction,             // ANCESTORS(up) / DESCENDANTS(down) / BOTH
    Integer maxDepth,                       // Max traversal depth, optional
    String domainCode,
    Map<String, Object> context
) {}