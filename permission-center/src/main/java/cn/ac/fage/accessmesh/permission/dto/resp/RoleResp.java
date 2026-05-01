package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record RoleResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    Long parentId,
    String roleTypeCode,
    String roleTypeName,
    String externalId,
    String name,
    Integer status,
    Integer sortOrder,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
