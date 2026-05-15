package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统定时任务实体类
 * <p>
 * 对应数据库表sys_job，用于存储定时任务配置。
 * 包括任务名称、执行目标、Cron表达式、执行策略等。
 * </p>
 */
@Getter
@Setter
@Table("sys_job")
public class SysJob {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务分组
     */
    private String jobGroup;

    /**
     * 执行目标（方法名或Bean名称）
     */
    private String invokeTarget;

    /**
     * Cron表达式
     */
    private String cronExpression;

    /**
     * 错过执行策略
     */
    private Integer misfirePolicy;

    /**
     * 执行用户ID
     */
    private Long runAsUserId;

    /**
        * 状态（0=停用，1=启用）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除人ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，1=已删除）
     */
    private Long deleteFlag;
}