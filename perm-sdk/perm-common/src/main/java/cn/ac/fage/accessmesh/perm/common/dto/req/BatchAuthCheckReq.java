package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Batch permission check request.
 * Checks multiple resources/operations in a single call.
 */
public record BatchAuthCheckReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotEmpty List<AuthCheckItem> items,
    Map<String, Object> context
) {

    /**
     * Single item in batch permission check.
     */
    public record AuthCheckItem(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        @NotBlank String operationCode,
        String domainCode,
        String codeType,
        String inheritMode
    ) {}
}