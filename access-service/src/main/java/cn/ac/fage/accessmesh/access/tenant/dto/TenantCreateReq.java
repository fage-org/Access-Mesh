package cn.ac.fage.accessmesh.access.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantCreateReq(@NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,63}") String code,
                              @NotBlank @Size(max = 128) String name) {}
