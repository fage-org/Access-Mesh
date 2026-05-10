package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 同步重试标记失败请求记录类
 * <p>
 * 用于手动标记同步重试任务为失败状态的请求参数。
 * 当重试无法成功时，管理员可手动标记失败并记录原因。
 * </p>
 *
 * @param id    重试记录ID（必填）
 * @param error 错误原因（必填）
 */
public record SyncRetryMarkFailedReq(
    /**
     * 重试记录ID
     */
    @NotNull(message = "重试记录ID不能为空")
    Long id,

    /**
     * 错误原因
     */
    @NotBlank(message = "错误原因不能为空")
    String error
) {}