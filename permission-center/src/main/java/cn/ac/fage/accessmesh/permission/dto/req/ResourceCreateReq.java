package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
