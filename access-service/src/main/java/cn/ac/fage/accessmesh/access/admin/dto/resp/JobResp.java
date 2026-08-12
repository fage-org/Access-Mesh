package cn.ac.fage.accessmesh.access.admin.dto.resp;

import cn.ac.fage.accessmesh.access.admin.entity.SysJob;

import java.time.LocalDateTime;

/**
 * 定时任务响应记录类
 * <p>
 * 用于返回定时任务配置查询结果。
 * 隐藏敏感字段：invokeTarget（执行命令）、runAsUserId（执行用户）、
 * jobGroup（内部分组）、misfirePolicy（内部策略）、tenantId（租户隔离）等。
 * </p>
 *
 * @param id             任务ID
 * @param jobName        任务名称
 * @param cronExpression Cron表达式
 * @param status         状态（0=停用，1=启用）
 * @param description    任务描述
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record JobResp(
    /**
     * 任务ID
     */
    Long id,

    /**
     * 任务名称
     */
    String jobName,

    /**
     * Cron表达式
     */
    String cronExpression,

    /**
        * 状态（0=停用，1=启用）
     */
    Integer status,

    /**
     * 任务描述
     */
    String description,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为响应DTO（隐藏敏感字段）
     *
     * @param entity 定时任务实体
     * @return 定时任务响应DTO，entity为null时返回null
     */
    public static JobResp from(SysJob entity) {
        if (entity == null) {
            return null;
        }
        return new JobResp(
            entity.getId(),
            entity.getJobName(),
            entity.getCronExpression(),
            entity.getStatus(),
            entity.getRemark(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}