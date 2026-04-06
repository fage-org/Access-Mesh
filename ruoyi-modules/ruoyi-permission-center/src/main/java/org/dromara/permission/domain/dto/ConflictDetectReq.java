package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 冲突检测请求（支持按主体/资源/域分页扫描）
 */
@Data
public class ConflictDetectReq {
    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    private Long abstractUserId;

    private Long abstractRoleId;

    private Long bizDomainId;

    private Long resourceEntityId;

    @Min(value = 1, message = "pageNum最小为1")
    private Integer pageNum;

    @Min(value = 1, message = "pageSize最小为1")
    private Integer pageSize;

    private String requestId;
}
