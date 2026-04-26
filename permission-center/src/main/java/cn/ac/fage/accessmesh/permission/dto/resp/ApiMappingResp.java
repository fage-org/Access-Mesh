package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record ApiMappingResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    Long resourceEntityId,
    String serviceCode,
    String httpMethod,
    String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
