package cn.ac.fage.accessmesh.access.tenant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

public record TenantStatusReq(@NotNull @Min(1) Long id, @NotNull @Min(0) @Max(1) Integer status) {}
