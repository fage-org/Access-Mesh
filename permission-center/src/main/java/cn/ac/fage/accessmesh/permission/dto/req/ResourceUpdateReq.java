package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceUpdateReq(
    @NotNull Long id,
    String code,
    String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra
) {}
