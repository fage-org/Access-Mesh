package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record ConditionResp(
    Long id,
    Long tenantId,
    String code,
    String name,
    String conditionRules,
    Boolean enabled,
    String description,
    LocalDateTime createdAt
) {}
