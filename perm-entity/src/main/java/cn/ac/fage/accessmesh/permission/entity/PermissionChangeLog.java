package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("permission_change_log")
public class PermissionChangeLog {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long bizDomainId;
    private String entityType;
    private Long entityId;
    private String operation;
    private String oldSnapshot;
    private String newSnapshot;
    private String diffSnapshot;
    private Long[] affectedAbstractUserIds;
    private Long[] affectedAbstractRoleIds;
    private String changeReason;
    private String changeSource;
    private String requestId;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getBizDomainId() { return bizDomainId; }
    public void setBizDomainId(Long bizDomainId) { this.bizDomainId = bizDomainId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getOldSnapshot() { return oldSnapshot; }
    public void setOldSnapshot(String oldSnapshot) { this.oldSnapshot = oldSnapshot; }
    public String getNewSnapshot() { return newSnapshot; }
    public void setNewSnapshot(String newSnapshot) { this.newSnapshot = newSnapshot; }
    public String getDiffSnapshot() { return diffSnapshot; }
    public void setDiffSnapshot(String diffSnapshot) { this.diffSnapshot = diffSnapshot; }
    public Long[] getAffectedAbstractUserIds() { return affectedAbstractUserIds; }
    public void setAffectedAbstractUserIds(Long[] affectedAbstractUserIds) { this.affectedAbstractUserIds = affectedAbstractUserIds; }
    public Long[] getAffectedAbstractRoleIds() { return affectedAbstractRoleIds; }
    public void setAffectedAbstractRoleIds(Long[] affectedAbstractRoleIds) { this.affectedAbstractRoleIds = affectedAbstractRoleIds; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getChangeSource() { return changeSource; }
    public void setChangeSource(String changeSource) { this.changeSource = changeSource; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
