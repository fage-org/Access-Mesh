package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Update resource dependency: id locates the record, operation codes use stable business keys.
 */
public record ResourceDependencyUpdateReq(
    @NotNull Long id,
    List<String> sourceOperationCodes,
    String sourceResourceTypeCode,
    List<String> requiredOperationCodes,
    String targetResourceTypeCode,
    Boolean autoGrant,
    String description
) {}
