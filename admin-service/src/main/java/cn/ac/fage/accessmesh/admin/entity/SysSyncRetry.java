package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统同步重试实体类
 * <p>
 * 对应数据库表sys_sync_retry，用于存储跨服务同步失败后的重试记录。
 * 包括消息键、目标服务、实体类型、操作类型、重试次数等。
 * </p>
 */
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
     * 获取消息键
     *
     * @return 消息键
     */
    public String getMessageKey() { return messageKey; }

    /**
     * 设置消息键
     *
     * @param messageKey 消息键
     */
    public void setMessageKey(String messageKey) { this.messageKey = messageKey; }

    /**
     * 获取目标服务名称
     *
     * @return 目标服务名称
     */
    public String getTargetService() { return targetService; }

    /**
     * 设置目标服务名称
     *
     * @param targetService 目标服务名称
     */
    public void setTargetService(String targetService) { this.targetService = targetService; }

    /**
     * 获取实体类型
     *
     * @return 实体类型
     */
    public String getEntityType() { return entityType; }

    /**
     * 设置实体类型
     *
     * @param entityType 实体类型
     */
    public void setEntityType(String entityType) { this.entityType = entityType; }

    /**
     * 获取外部ID
     *
     * @return 外部ID
     */
    public String getExternalId() { return externalId; }

    /**
     * 设置外部ID
     *
     * @param externalId 外部ID
     */
    public void setExternalId(String externalId) { this.externalId = externalId; }

    /**
     * 获取操作类型
     *
     * @return 操作类型
     */
    public String getOperationType() { return operationType; }

    /**
     * 设置操作类型
     *
     * @param operationType 操作类型
     */
    public void setOperationType(String operationType) { this.operationType = operationType; }

    /**
     * 获取消息内容
     *
     * @return 消息内容
     */
    public String getPayload() { return payload; }

    /**
     * 设置消息内容
     *
     * @param payload 消息内容
     */
    public void setPayload(String payload) { this.payload = payload; }

    /**
     * 获取重试次数
     *
     * @return 重试次数
     */
    public Integer getRetryCount() { return retryCount; }

    /**
     * 设置重试次数
     *
     * @param retryCount 重试次数
     */
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    public Integer getMaxRetries() { return maxRetries; }

    /**
     * 设置最大重试次数
     *
     * @param maxRetries 最大重试次数
     */
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    /**
     * 获取下次重试时间
     *
     * @return 下次重试时间
     */
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }

    /**
     * 设置下次重试时间
     *
     * @param nextRetryAt 下次重试时间
     */
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    /**
     * 获取最后错误信息
     *
     * @return 最后错误信息
     */
    public String getLastError() { return lastError; }

    /**
     * 设置最后错误信息
     *
     * @param lastError 最后错误信息
     */
    public void setLastError(String lastError) { this.lastError = lastError; }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public String getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(String status) { this.status = status; }

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