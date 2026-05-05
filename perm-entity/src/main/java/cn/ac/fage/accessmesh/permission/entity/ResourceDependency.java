package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("resource_dependency")
public class ResourceDependency {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long resourceEntityId;
    private Long dependsOnResourceEntityId;
    private Long sourceOperationBits;
    private Long requiredOperationBits;
    private Boolean autoGrant;
    private String description;
    private String ownerServiceCode;
    private String maintainSource;
    private String syncKey;
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
    public Long getResourceEntityId() { return resourceEntityId; }
    public void setResourceEntityId(Long resourceEntityId) { this.resourceEntityId = resourceEntityId; }
    public Long getDependsOnResourceEntityId() { return dependsOnResourceEntityId; }
    public void setDependsOnResourceEntityId(Long dependsOnResourceEntityId) { this.dependsOnResourceEntityId = dependsOnResourceEntityId; }
    public Long getSourceOperationBits() { return sourceOperationBits; }
    public void setSourceOperationBits(Long sourceOperationBits) { this.sourceOperationBits = sourceOperationBits; }
    public Long getRequiredOperationBits() { return requiredOperationBits; }
    public void setRequiredOperationBits(Long requiredOperationBits) { this.requiredOperationBits = requiredOperationBits; }
    public Boolean getAutoGrant() { return autoGrant; }
    public void setAutoGrant(Boolean autoGrant) { this.autoGrant = autoGrant; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerServiceCode() { return ownerServiceCode; }
    public void setOwnerServiceCode(String ownerServiceCode) { this.ownerServiceCode = ownerServiceCode; }
    public String getMaintainSource() { return maintainSource; }
    public void setMaintainSource(String maintainSource) { this.maintainSource = maintainSource; }
    public String getSyncKey() { return syncKey; }
    public void setSyncKey(String syncKey) { this.syncKey = syncKey; }
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
