package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统同步重试实体类
 * <p>
 * 对应数据库表sys_sync_retry，用于存储跨服务同步失败后的重试记录。
 * 包括消息键、目标服务、实体类型、操作类型、重试次数等。
 * </p>
 */
@Getter
@Setter
@Table("sys_sync_retry")
public class SysSyncRetry {

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
     * 消息键（用于幂等处理）
     */
    private String messageKey;

    /**
     * 目标服务名称
     */
    private String targetService;

    /**
     * 实体类型
     */
    private String entityType;

    /**
     * 外部ID（如permission-center的用户ID）
     */
    private String externalId;

    /**
     * 操作类型（CREATE/UPDATE/DELETE）
     */
    private String operationType;

    /**
     * 消息内容（JSON格式）
     */
    private String payload;

    /**
     * 当前重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryAt;

    /**
     * 最后错误信息
     */
    private String lastError;

    /**
     * 状态（PENDING/RETRYING/SUCCESS/FAILED）
     */
    private String status;

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