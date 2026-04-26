package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record ServiceConfigResp(
    Long id,
    Long tenantId,
    String serviceCode,
    String name,
    String basePath,
    String description,
    Integer status,
    String extra,
    LocalDateTime createdAt
) {}
