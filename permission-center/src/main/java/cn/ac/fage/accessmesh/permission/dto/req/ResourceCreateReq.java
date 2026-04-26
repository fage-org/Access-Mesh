package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
