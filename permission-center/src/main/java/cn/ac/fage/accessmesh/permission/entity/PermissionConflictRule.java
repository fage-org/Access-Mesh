package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("permission_conflict_rule")
public class PermissionConflictRule {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long bizDomainId;
    private String conflictType;
    private Long firstOperationPermissionId;
    private Long secondOperationPermissionId;
    private Integer resourceTypeValue;
    private Long firstAbstractRoleId;
    private Long secondAbstractRoleId;
    private String description;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getBizDomainId() { return bizDomainId; }
    public void setBizDomainId(Long bizDomainId) { this.bizDomainId = bizDomainId; }
    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }
    public Long getFirstOperationPermissionId() { return firstOperationPermissionId; }
    public void setFirstOperationPermissionId(Long firstOperationPermissionId) { this.firstOperationPermissionId = firstOperationPermissionId; }
    public Long getSecondOperationPermissionId() { return secondOperationPermissionId; }
    public void setSecondOperationPermissionId(Long secondOperationPermissionId) { this.secondOperationPermissionId = secondOperationPermissionId; }
    public Integer getResourceTypeValue() { return resourceTypeValue; }
    public void setResourceTypeValue(Integer resourceTypeValue) { this.resourceTypeValue = resourceTypeValue; }
    public Long getFirstAbstractRoleId() { return firstAbstractRoleId; }
    public void setFirstAbstractRoleId(Long firstAbstractRoleId) { this.firstAbstractRoleId = firstAbstractRoleId; }
    public Long getSecondAbstractRoleId() { return secondAbstractRoleId; }
    public void setSecondAbstractRoleId(Long secondAbstractRoleId) { this.secondAbstractRoleId = secondAbstractRoleId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}
