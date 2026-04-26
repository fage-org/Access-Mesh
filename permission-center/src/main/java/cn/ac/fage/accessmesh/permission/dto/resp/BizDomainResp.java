package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record BizDomainResp(
    Long id,
    Long tenantId,
    String code,
    String name,
    String description,
    LocalDateTime createdAt
) {}
