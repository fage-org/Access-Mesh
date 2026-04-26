package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record UserResp(
    Long id,
    Long tenantId,
    Integer userType,
    String externalId,
    String name,
    Boolean enabled,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
