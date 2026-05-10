package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 权限变更日志实体
 * <p>
 * 表示权限变更的详细记录日志。
 * 记录变更前后数据快照、差异信息、受影响的用户和角色等。
 * 用于权限审计、变更追溯和合规性检查。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("permission_change_log")
public class PermissionChangeLog {

    /**
     * 权限变更日志唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 所属业务域ID
     */
    private Long bizDomainId;

    /**
     * 实体类型（ROLE/RESOURCE/PERMISSION等）
     */
    private String entityType;

    /**
     * 实体ID
     */
    private Long entityId;

    /**
     * 操作类型（CREATE/UPDATE/DELETE）
     */
    private String operation;

    /**
     * 变更前数据快照（JSON格式）
     */
    private String oldSnapshot;

    /**
     * 变更后数据快照（JSON格式）
     */
    private String newSnapshot;

    /**
     * 差异快照（JSON格式），记录变更的具体差异
     */
    private String diffSnapshot;

    /**
     * 受影响的用户ID数组
     */
    private Long[] affectedAbstractUserIds;

    /**
     * 受影响的角色ID数组
     */
    private Long[] affectedAbstractRoleIds;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 变更来源（MANUAL/SYNC/API等）
     */
    private String changeSource;

    /**
     * 请求ID，用于关联请求链路
     */
    private String requestId;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 获取权限变更日志唯一标识
     *
     * @return 日志ID
     */
    public Long getId() { return id; }

    /**
     * 设置权限变更日志唯一标识
     *
     * @param id 日志ID
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
     * 获取所属业务域ID
     *
     * @return 业务域ID
     */
    public Long getBizDomainId() { return bizDomainId; }

    /**
     * 设置所属业务域ID
     *
     * @param bizDomainId 业务域ID
     */
    public void setBizDomainId(Long bizDomainId) { this.bizDomainId = bizDomainId; }

    /**
     * 获取实体类型
     *
     * @return 实体类型（ROLE/RESOURCE/PERMISSION等）
     */
    public String getEntityType() { return entityType; }

    /**
     * 设置实体类型
     *
     * @param entityType 实体类型
     */
    public void setEntityType(String entityType) { this.entityType = entityType; }

    /**
     * 获取实体ID
     *
     * @return 实体ID
     */
    public Long getEntityId() { return entityId; }

    /**
     * 设置实体ID
     *
     * @param entityId 实体ID
     */
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    /**
     * 获取操作类型
     *
     * @return 操作类型（CREATE/UPDATE/DELETE）
     */
    public String getOperation() { return operation; }

    /**
     * 设置操作类型
     *
     * @param operation 操作类型
     */
    public void setOperation(String operation) { this.operation = operation; }

    /**
     * 获取变更前数据快照
     *
     * @return 变更前数据快照（JSON格式）
     */
    public String getOldSnapshot() { return oldSnapshot; }

    /**
     * 设置变更前数据快照
     *
     * @param oldSnapshot 变更前数据快照（JSON格式）
     */
    public void setOldSnapshot(String oldSnapshot) { this.oldSnapshot = oldSnapshot; }

    /**
     * 获取变更后数据快照
     *
     * @return 变更后数据快照（JSON格式）
     */
    public String getNewSnapshot() { return newSnapshot; }

    /**
     * 设置变更后数据快照
     *
     * @param newSnapshot 变更后数据快照（JSON格式）
     */
    public void setNewSnapshot(String newSnapshot) { this.newSnapshot = newSnapshot; }

    /**
     * 获取差异快照
     *
     * @return 差异快照（JSON格式）
     */
    public String getDiffSnapshot() { return diffSnapshot; }

    /**
     * 设置差异快照
     *
     * @param diffSnapshot 差异快照（JSON格式）
     */
    public void setDiffSnapshot(String diffSnapshot) { this.diffSnapshot = diffSnapshot; }

    /**
     * 获取受影响的用户ID数组
     *
     * @return 受影响的用户ID数组
     */
    public Long[] getAffectedAbstractUserIds() { return affectedAbstractUserIds; }

    /**
     * 设置受影响的用户ID数组
     *
     * @param affectedAbstractUserIds 受影响的用户ID数组
     */
    public void setAffectedAbstractUserIds(Long[] affectedAbstractUserIds) { this.affectedAbstractUserIds = affectedAbstractUserIds; }

    /**
     * 获取受影响的角色ID数组
     *
     * @return 受影响的角色ID数组
     */
    public Long[] getAffectedAbstractRoleIds() { return affectedAbstractRoleIds; }

    /**
     * 设置受影响的角色ID数组
     *
     * @param affectedAbstractRoleIds 受影响的角色ID数组
     */
    public void setAffectedAbstractRoleIds(Long[] affectedAbstractRoleIds) { this.affectedAbstractRoleIds = affectedAbstractRoleIds; }

    /**
     * 获取变更原因
     *
     * @return 变更原因
     */
    public String getChangeReason() { return changeReason; }

    /**
     * 设置变更原因
     *
     * @param changeReason 变更原因
     */
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    /**
     * 获取变更来源
     *
     * @return 变更来源（MANUAL/SYNC/API等）
     */
    public String getChangeSource() { return changeSource; }

    /**
     * 设置变更来源
     *
     * @param changeSource 变革来源
     */
    public void setChangeSource(String changeSource) { this.changeSource = changeSource; }

    /**
     * 获取请求ID
     *
     * @return 请求ID
     */
    public String getRequestId() { return requestId; }

    /**
     * 设置请求ID
     *
     * @param requestId 请求ID
     */
    public void setRequestId(String requestId) { this.requestId = requestId; }

    /**
     * 获取创建者用户ID
     *
     * @return 创建者用户ID
     */
    public Long getCreatedBy() { return createdBy; }

    /**
     * 设置创建者用户ID
     *
     * @param createdBy 创建者用户ID
     */
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

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