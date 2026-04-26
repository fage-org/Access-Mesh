package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    Long parentId,
    Integer resourceType,
    String resourceTypeName,
    String code,
    String codeType,
    String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
