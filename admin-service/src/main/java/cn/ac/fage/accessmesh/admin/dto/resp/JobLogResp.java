package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysJobLog;

import java.time.LocalDateTime;

/**
 * 定时任务日志响应记录类
 * <p>
 * 用于返回定时任务执行日志查询结果。
 * 隐藏敏感字段：invokeTarget（执行命令）、message（错误堆栈）、tenantId（租户隔离）。
 * </p>
 *
 * @param id        任务日志ID
 * @param jobId     任务ID
 * @param jobName   任务名称
 * @param status    执行状态（0=成功，1=失败）
 * @param costTime  执行耗时（毫秒）
 * @param createdAt 创建时间
 */
public record JobLogResp(
    /**
     * 任务日志ID
     */
    Long id,

    /**
     * 任务ID
     */
    Long jobId,

    /**
     * 任务名称
     */
    String jobName,

    /**
     * 执行状态（0=成功，1=失败）
     */
    Integer status,

    /**
     * 执行耗时（毫秒）
     */
    Integer costTime,

    /**
     * 创建时间
     */
    LocalDateTime createdAt
) {
    /**
     * 从实体转换为响应DTO（隐藏敏感字段）
     *
     * @param entity 任务日志实体
     * @return 任务日志响应DTO，entity为null时返回null
     */
    public static JobLogResp from(SysJobLog entity) {
        if (entity == null) {
            return null;
        }
        return new JobLogResp(
            entity.getId(),
            entity.getJobId(),
            entity.getJobName(),
            entity.getStatus(),
            entity.getCostTime(),
            entity.getCreatedAt()
        );
    }
}