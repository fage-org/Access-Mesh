package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record ResourceDependencyResp(
    Long id,
    Long tenantId,
    Long resourceEntityId,
    String sourceResourceCode,
    Long dependsOnResourceEntityId,
    String depResourceCode,
    Long sourceOperationBits,
    Long requiredOperationBits,
    Boolean autoGrant,
    String description,
    LocalDateTime createdAt
) {}
