package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Shared: create a resource in permission-center (used to sync menu→resource).
 */
public record ResourceCreateReq(
    Long bizDomainId,
    Long parentId,
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    String codeType,
    @NotBlank String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra
) {}
