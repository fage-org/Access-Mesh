package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record TypeDefinitionResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String typeKey,
    Integer typeValue,
    String name,
    String description,
    Boolean isSystem,
    Integer sortOrder,
    String extra,
    LocalDateTime createdAt
) {}
