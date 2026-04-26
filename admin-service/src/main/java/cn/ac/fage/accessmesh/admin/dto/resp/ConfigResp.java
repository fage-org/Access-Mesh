package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;

public record ConfigResp(
    Long id,
    String configName,
    String configKey,
    String configValue,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
