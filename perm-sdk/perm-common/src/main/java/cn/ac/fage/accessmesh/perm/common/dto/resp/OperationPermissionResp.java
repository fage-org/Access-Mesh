package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;

/**
 * Shared: operation permission details.
 */
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
