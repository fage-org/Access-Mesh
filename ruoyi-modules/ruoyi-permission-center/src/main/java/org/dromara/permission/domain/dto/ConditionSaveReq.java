package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConditionSaveReq {
    private Long id;

    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    @NotBlank(message = "code不能为空")
    @Size(max = 64, message = "code长度不能超过64")
    private String code;

    @NotBlank(message = "name不能为空")
    @Size(max = 128, message = "name长度不能超过128")
    private String name;

    @Size(max = 16, message = "conditionSource长度不能超过16")
    private String conditionSource;

    private String expression;

    @Size(max = 16, message = "status长度不能超过16")
    private String status;

    private Boolean enabled;

    @Size(max = 512, message = "description长度不能超过512")
    private String description;

    private Long reviewedBy;
}
