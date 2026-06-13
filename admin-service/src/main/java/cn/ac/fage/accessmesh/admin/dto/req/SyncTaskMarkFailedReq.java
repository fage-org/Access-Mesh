package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 同步任务标记失败请求记录类
 * <p>
 * 用于手动将同步任务标记为 {@code FAILED} 状态的请求参数。
 * 当任务无法继续重试时，管理员可通过此接口记录原因并终结任务。
 * </p>
 *
 * @param id    任务ID（必填）
 * @param error 错误原因（必填）
 */
public record SyncTaskMarkFailedReq(
    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    Long id,

    /**
     * 错误原因
     */
    @NotBlank(message = "错误原因不能为空")
    String error
) {}
