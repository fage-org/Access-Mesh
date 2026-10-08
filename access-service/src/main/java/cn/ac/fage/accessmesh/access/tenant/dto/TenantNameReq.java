package cn.ac.fage.accessmesh.access.tenant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TenantNameReq(@NotNull @Min(1) Long id, @NotBlank @Size(max = 128) String name) {}
