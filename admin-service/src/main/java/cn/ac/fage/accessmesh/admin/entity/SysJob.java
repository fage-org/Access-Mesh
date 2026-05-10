package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统定时任务实体类
 * <p>
 * 对应数据库表sys_job，用于存储定时任务配置。
 * 包括任务名称、执行目标、Cron表达式、执行策略等。
 * </p>
 */
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
     * 状态（0=正常，1=暂停）
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

    /**
     * 获取主键ID
     *
     * @return 主键ID
     */
    public Long getId() { return id; }

    /**
     * 设置主键ID
     *
     * @param id 主键ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() { return tenantId; }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    /**
     * 获取任务名称
     *
     * @return 任务名称
     */
    public String getJobName() { return jobName; }

    /**
     * 设置任务名称
     *
     * @param jobName 任务名称
     */
    public void setJobName(String jobName) { this.jobName = jobName; }

    /**
     * 获取任务分组
     *
     * @return 任务分组
     */
    public String getJobGroup() { return jobGroup; }

    /**
     * 设置任务分组
     *
     * @param jobGroup 任务分组
     */
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }

    /**
     * 获取执行目标
     *
     * @return 执行目标
     */
    public String getInvokeTarget() { return invokeTarget; }

    /**
     * 设置执行目标
     *
     * @param invokeTarget 执行目标
     */
    public void setInvokeTarget(String invokeTarget) { this.invokeTarget = invokeTarget; }

    /**
     * 获取Cron表达式
     *
     * @return Cron表达式
     */
    public String getCronExpression() { return cronExpression; }

    /**
     * 设置Cron表达式
     *
     * @param cronExpression Cron表达式
     */
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    /**
     * 获取错过执行策略
     *
     * @return 错过执行策略
     */
    public Integer getMisfirePolicy() { return misfirePolicy; }

    /**
     * 设置错过执行策略
     *
     * @param misfirePolicy 错过执行策略
     */
    public void setMisfirePolicy(Integer misfirePolicy) { this.misfirePolicy = misfirePolicy; }

    /**
     * 获取执行用户ID
     *
     * @return 执行用户ID
     */
    public Long getRunAsUserId() { return runAsUserId; }

    /**
     * 设置执行用户ID
     *
     * @param runAsUserId 执行用户ID
     */
    public void setRunAsUserId(Long runAsUserId) { this.runAsUserId = runAsUserId; }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取备注
     *
     * @return 备注
     */
    public String getRemark() { return remark; }

    /**
     * 设置备注
     *
     * @param remark 备注
     */
    public void setRemark(String remark) { this.remark = remark; }

    /**
     * 获取创建人ID
     *
     * @return 创建人ID
     */
    public Long getCreatedBy() { return createdBy; }

    /**
     * 设置创建人ID
     *
     * @param createdBy 创建人ID
     */
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    /**
     * 获取更新人ID
     *
     * @return 更新人ID
     */
    public Long getUpdatedBy() { return updatedBy; }

    /**
     * 设置更新人ID
     *
     * @param updatedBy 更新人ID
     */
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 获取删除人ID
     *
     * @return 删除人ID
     */
    public Long getDeletedBy() { return deletedBy; }

    /**
     * 设置删除人ID
     *
     * @param deletedBy 删除人ID
     */
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * 设置更新时间
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 获取删除时间
     *
     * @return 删除时间
     */
    public LocalDateTime getDeletedAt() { return deletedAt; }

    /**
     * 设置删除时间
     *
     * @param deletedAt 删除时间
     */
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 获取删除标记
     *
     * @return 删除标记
     */
    public Long getDeleteFlag() { return deleteFlag; }

    /**
     * 设置删除标记
     *
     * @param deleteFlag 删除标记
     */
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}