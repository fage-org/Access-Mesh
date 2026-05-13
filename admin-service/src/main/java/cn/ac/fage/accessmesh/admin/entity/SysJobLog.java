package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统定时任务日志实体类
 * <p>
 * 对应数据库表sys_job_log，用于记录定时任务的执行日志。
 * 包括任务名称、执行状态、执行消息、耗时等。
 * </p>
 */
@Getter
@Setter
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
}