package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 定时任务更新请求记录类
 *
 * @param id             任务ID（必填）
 * @param jobName        任务名称（可选）
 * @param jobGroup       任务分组（可选）
 * @param invokeTarget   执行目标（可选）
 * @param cronExpression Cron表达式（可选）
 * @param misfirePolicy  错过执行策略（可选）
 * @param status         状态（可选，0=停用，1=启用）
 * @param remark         备注（可选）
 */
public record JobUpdateReq(
    @NotNull(message = "任务ID不能为空")
    Long id,

    String jobName,

    String jobGroup,

    String invokeTarget,

    String cronExpression,

    Integer misfirePolicy,

    Integer status,

    String remark
) {}