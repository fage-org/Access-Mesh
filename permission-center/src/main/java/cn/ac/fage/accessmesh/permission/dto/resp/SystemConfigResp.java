package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record SystemConfigResp(
    Long id,
    Long tenantId,
    String configKey,
    String configValue,
    String description,
    LocalDateTime updatedAt
) {}
