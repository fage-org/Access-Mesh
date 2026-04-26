package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record DomainConfigResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String configType,
    String extra,
    LocalDateTime updatedAt
) {}
