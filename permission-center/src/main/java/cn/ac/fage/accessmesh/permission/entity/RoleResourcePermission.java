package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("role_resource_permission")
public class RoleResourcePermission {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long abstractRoleId;
    private Long resourceEntityId;
    private Long operationPermissionId;
    private Integer resourceType;
    private Long dependOn;
    private Boolean scopeAll;
    private Boolean canManage;
    private Long conditionId;
    private String grantSource;
    private Long grantDepId;
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
    public Long getAbstractRoleId() { return abstractRoleId; }
    public void setAbstractRoleId(Long abstractRoleId) { this.abstractRoleId = abstractRoleId; }
    public Long getResourceEntityId() { return resourceEntityId; }
    public void setResourceEntityId(Long resourceEntityId) { this.resourceEntityId = resourceEntityId; }
    public Long getOperationPermissionId() { return operationPermissionId; }
    public void setOperationPermissionId(Long operationPermissionId) { this.operationPermissionId = operationPermissionId; }
    public Integer getResourceType() { return resourceType; }
    public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }
    public Long getDependOn() { return dependOn; }
    public void setDependOn(Long dependOn) { this.dependOn = dependOn; }
    public Boolean getScopeAll() { return scopeAll; }
    public void setScopeAll(Boolean scopeAll) { this.scopeAll = scopeAll; }
    public Boolean getCanManage() { return canManage; }
    public void setCanManage(Boolean canManage) { this.canManage = canManage; }
    public Long getConditionId() { return conditionId; }
    public void setConditionId(Long conditionId) { this.conditionId = conditionId; }
    public String getGrantSource() { return grantSource; }
    public void setGrantSource(String grantSource) { this.grantSource = grantSource; }
    public Long getGrantDepId() { return grantDepId; }
    public void setGrantDepId(Long grantDepId) { this.grantDepId = grantDepId; }
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
