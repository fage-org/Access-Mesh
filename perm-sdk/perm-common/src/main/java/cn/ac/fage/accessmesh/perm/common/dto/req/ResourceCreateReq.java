package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Shared: create a resource in permission-center (used to sync menu→resource).
 */
public record ResourceCreateReq(
    @NotNull Long tenantId,
    Long bizDomainId,
    Long parentId,
    @NotNull Integer resourceType,
    @NotBlank String code,
    String codeType,
    @NotBlank String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra
) {}
