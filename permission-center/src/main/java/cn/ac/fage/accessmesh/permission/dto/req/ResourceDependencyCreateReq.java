package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Create resource dependency using stable business keys (consistent with batch-sync §6.9).
 */
public record ResourceDependencyCreateReq(
    @NotBlank String sourceResourceTypeCode,
    @NotBlank String sourceResourceCode,
    String sourceCodeType,
    List<String> sourceOperationCodes,
    @NotBlank String targetResourceTypeCode,
    @NotBlank String targetResourceCode,
    String targetCodeType,
    @NotEmpty List<String> requiredOperationCodes,
    Boolean autoGrant,
    String description
) {}
