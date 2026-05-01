package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Batch sync resource dependencies per §6.9 contract.
 */
public record DependencyBatchSyncReq(
    @NotBlank String serviceCode,
    @NotBlank String maintainSource,
    String syncMode,
    List<DependencySyncItem> items
) {
    public record DependencySyncItem(
        @NotBlank String sourceResourceTypeCode,
        @NotBlank String sourceResourceCode,
        String sourceCodeType,
        List<String> sourceOperationCodes,
        @NotBlank String targetResourceTypeCode,
        @NotBlank String targetResourceCode,
        String targetCodeType,
        List<String> requiredOperationCodes,
        Boolean autoGrant,
        String description
    ) {}
}
