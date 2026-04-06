package org.dromara.permission.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionConditionVo {
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String conditionSource;
    private String expression;
    private String status;
    private Boolean enabled;
    private String description;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
