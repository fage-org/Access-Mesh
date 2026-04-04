package org.dromara.permission.domain.dto;

import lombok.Data;

@Data
public class ConditionSaveReq {
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String conditionSource;
    private String expression;
    private String status;
    private String description;
    private Long reviewedBy;
}
