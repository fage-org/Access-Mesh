package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 冲突规则保存请求（单条，存库前保证 first < second）
 */
@Data
public class ConflictRuleSaveReq {
    private Long id;

    @NotNull(message = "tenantId不能为空")
    private Long tenantId;

    private Long bizDomainId;

    private String requestId;

    private String changeSource;

    private String changeReason;

    @NotNull(message = "firstOperationPermissionId不能为空")
    private Long firstOperationPermissionId;

    @NotNull(message = "secondOperationPermissionId不能为空")
    private Long secondOperationPermissionId;

    /** 仅当资源类型为该值时生效，NULL 表示所有资源类型 */
    private Integer resourceTypeValue;
}
