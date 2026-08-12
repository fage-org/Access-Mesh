package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 定时任务创建请求记录类
 *
 * @param jobName        任务名称（必填）
 * @param jobGroup       任务分组（可选）
 * @param invokeTarget   执行目标（必填）
 * @param cronExpression Cron表达式（必填）
 * @param misfirePolicy  错过执行策略（可选，1=立即执行，2=执行一次，3=放弃）
 * @param status         状态（可选，0=停用，1=启用）
 * @param remark         备注（可选）
 */
public record JobCreateReq(
    @NotBlank(message = "任务名称不能为空")
    String jobName,

    String jobGroup,

    @NotBlank(message = "执行目标不能为空")
    String invokeTarget,

    @NotBlank(message = "Cron表达式不能为空")
    String cronExpression,

    Integer misfirePolicy,

    Integer status,

    String remark
) {}