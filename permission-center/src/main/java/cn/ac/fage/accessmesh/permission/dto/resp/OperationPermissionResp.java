package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record OperationPermissionResp(
    Long id,
    Long tenantId,
    Integer resourceType,
    String resourceTypeName,
    String code,
    String name,
    Long binaryBit,
    Long inheritMask,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
