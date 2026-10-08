package cn.ac.fage.accessmesh.access.audit.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PlatformAuditPageReq(@Min(1) Integer pageNum, @Min(1) @Max(200) Integer pageSize,
                                   @Min(1) Long targetTenantId) {}
