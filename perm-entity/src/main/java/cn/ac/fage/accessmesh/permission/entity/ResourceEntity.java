package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("resource_entity")
public class ResourceEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long bizDomainId;
    private Long parentId;
    private Integer resourceType;
    private String code;
    private String codeType;
    private String name;
    private String path;
    private Integer status;
    private Integer sortOrder;
    private String ownerServiceCode;
    private String maintainSource;
    private String syncKey;
    private String extra;
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
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getResourceType() { return resourceType; }
    public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getOwnerServiceCode() { return ownerServiceCode; }
    public void setOwnerServiceCode(String ownerServiceCode) { this.ownerServiceCode = ownerServiceCode; }
    public String getMaintainSource() { return maintainSource; }
    public void setMaintainSource(String maintainSource) { this.maintainSource = maintainSource; }
    public String getSyncKey() { return syncKey; }
    public void setSyncKey(String syncKey) { this.syncKey = syncKey; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
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
