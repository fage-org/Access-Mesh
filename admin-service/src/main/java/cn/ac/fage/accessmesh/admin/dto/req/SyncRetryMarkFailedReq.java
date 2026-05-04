package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncRetryMarkFailedReq(
    @NotNull(message = "重试记录ID不能为空")
    Long id,
    @NotBlank(message = "错误原因不能为空")
    String error
) {}
