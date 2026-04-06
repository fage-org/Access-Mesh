package org.dromara.permission.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通用 ID 列表删除请求
 */
@Data
public class IdsReq {
    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    private String requestId;

    private String changeSource;

    private String changeReason;

    @NotEmpty(message = "ids不能为空")
    @Size(max = 100, message = "单次删除不得超过100条")
    private List<Long> ids;
}
