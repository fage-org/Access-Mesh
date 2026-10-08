package cn.ac.fage.accessmesh.access.tenant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

public record TenantPageReq(@Min(1) Integer pageNum, @Min(1) @Max(200) Integer pageSize, @Size(max = 128) String keyword) {}
