package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统定时任务日志实体类
 * <p>
 * 对应数据库表sys_job_log，用于记录定时任务的执行日志。
 * 包括任务名称、执行状态、执行消息、耗时等。
 * </p>
 */
@Table("sys_job_log")
public class SysJobLog {

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
     * 任务ID
     */
    private Long jobId;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 执行目标
     */
    private String invokeTarget;

    /**
     * 执行状态（0=成功，1=失败）
     */
    private Integer status;

    /**
     * 执行消息
     */
    private String message;

    /**
     * 执行耗时（毫秒）
     */
    private Integer costTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

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
     * 获取任务ID
     *
     * @return 任务ID
     */
    public Long getJobId() { return jobId; }

    /**
     * 设置任务ID
     *
     * @param jobId 任务ID
     */
    public void setJobId(Long jobId) { this.jobId = jobId; }

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
     * 获取执行状态
     *
     * @return 执行状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置执行状态
     *
     * @param status 执行状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取执行消息
     *
     * @return 执行消息
     */
    public String getMessage() { return message; }

    /**
     * 设置执行消息
     *
     * @param message 执行消息
     */
    public void setMessage(String message) { this.message = message; }

    /**
     * 获取执行耗时
     *
     * @return 执行耗时（毫秒）
     */
    public Integer getCostTime() { return costTime; }

    /**
     * 设置执行耗时
     *
     * @param costTime 执行耗时（毫秒）
     */
    public void setCostTime(Integer costTime) { this.costTime = costTime; }

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
}