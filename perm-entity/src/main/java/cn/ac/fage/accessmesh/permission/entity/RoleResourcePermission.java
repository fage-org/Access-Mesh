package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 角色资源权限实体
 * <p>
 * 表示角色对资源的操作权限配置。
 * 定义了角色可以执行哪些操作（operationPermissionId）、
 * 操作的资源范围（resourceEntityId）、权限条件（conditionId）等。
 * 支持权限继承（dependOn）和授权传递（canGrant）。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("role_resource_permission")
public class RoleResourcePermission {

    /**
     * 角色资源权限唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 抽象角色ID
     */
    private Long abstractRoleId;

    /**
     * 资源实体ID
     */
    private Long resourceEntityId;

    /**
     * 操作权限ID
     */
    private Long operationPermissionId;

    /**
     * 资源类型
     */
    private Integer resourceType;

    /**
     * 依赖的权限ID，用于权限继承关系
     */
    private Long dependOn;

    /**
     * 是否全部范围（true=全部范围，false=限定范围）
     */
    private Boolean scopeAll;

    /**
     * 是否可授权（true=可授权给他人，false=不可）
     */
    private Boolean canGrant;

    /**
     * 权限条件ID，用于动态权限判断
     */
    private Long conditionId;

    /**
     * 授权来源，标识权限授予方式
     */
    private String grantSource;

    /**
     * 授权依赖ID
     */
    private Long grantDepId;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 最后更新者用户ID
     */
    private Long updatedBy;

    /**
     * 删除者用户ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，其他=已删除）
     */
    private Long deleteFlag;

    /**
     * 获取角色资源权限唯一标识
     *
     * @return 权限ID
     */
    public Long getId() { return id; }

    /**
     * 设置角色资源权限唯一标识
     *
     * @param id 权限ID
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
     * 获取抽象角色ID
     *
     * @return 抽象角色ID
     */
    public Long getAbstractRoleId() { return abstractRoleId; }

    /**
     * 设置抽象角色ID
     *
     * @param abstractRoleId 抽象角色ID
     */
    public void setAbstractRoleId(Long abstractRoleId) { this.abstractRoleId = abstractRoleId; }

    /**
     * 获取资源实体ID
     *
     * @return 资源实体ID
     */
    public Long getResourceEntityId() { return resourceEntityId; }

    /**
     * 设置资源实体ID
     *
     * @param resourceEntityId 资源实体ID
     */
    public void setResourceEntityId(Long resourceEntityId) { this.resourceEntityId = resourceEntityId; }

    /**
     * 获取操作权限ID
     *
     * @return 操作权限ID
     */
    public Long getOperationPermissionId() { return operationPermissionId; }

    /**
     * 设置操作权限ID
     *
     * @param operationPermissionId 操作权限ID
     */
    public void setOperationPermissionId(Long operationPermissionId) { this.operationPermissionId = operationPermissionId; }

    /**
     * 获取资源类型
     *
     * @return 资源类型
     */
    public Integer getResourceType() { return resourceType; }

    /**
     * 设置资源类型
     *
     * @param resourceType 资源类型
     */
    public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }

    /**
     * 获取依赖的权限ID
     *
     * @return 依赖的权限ID，用于权限继承关系
     */
    public Long getDependOn() { return dependOn; }

    /**
     * 设置依赖的权限ID
     *
     * @param dependOn 依赖的权限ID
     */
    public void setDependOn(Long dependOn) { this.dependOn = dependOn; }

    /**
     * 获取是否全部范围
     *
     * @return 是否全部范围（true=全部范围，false=限定范围）
     */
    public Boolean getScopeAll() { return scopeAll; }

    /**
     * 设置是否全部范围
     *
     * @param scopeAll 是否全部范围
     */
    public void setScopeAll(Boolean scopeAll) { this.scopeAll = scopeAll; }

    /**
     * 获取是否可授权
     *
     * @return 是否可授权（true=可授权给他人，false=不可）
     */
    public Boolean getCanGrant() { return canGrant; }

    /**
     * 设置是否可授权
     *
     * @param canGrant 是否可授权
     */
    public void setCanGrant(Boolean canGrant) { this.canGrant = canGrant; }

    /**
     * 获取权限条件ID
     *
     * @return 权限条件ID，用于动态权限判断
     */
    public Long getConditionId() { return conditionId; }

    /**
     * 设置权限条件ID
     *
     * @param conditionId 权限条件ID
     */
    public void setConditionId(Long conditionId) { this.conditionId = conditionId; }

    /**
     * 获取授权来源
     *
     * @return 授权来源
     */
    public String getGrantSource() { return grantSource; }

    /**
     * 设置授权来源
     *
     * @param grantSource 授权来源
     */
    public void setGrantSource(String grantSource) { this.grantSource = grantSource; }

    /**
     * 获取授权依赖ID
     *
     * @return 授权依赖ID
     */
    public Long getGrantDepId() { return grantDepId; }

    /**
     * 设置授权依赖ID
     *
     * @param grantDepId 授权依赖ID
     */
    public void setGrantDepId(Long grantDepId) { this.grantDepId = grantDepId; }

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
     * 获取最后更新者用户ID
     *
     * @return 最后更新者用户ID
     */
    public Long getUpdatedBy() { return updatedBy; }

    /**
     * 设置最后更新者用户ID
     *
     * @param updatedBy 最后更新者用户ID
     */
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 获取删除者用户ID
     *
     * @return 删除者用户ID
     */
    public Long getDeletedBy() { return deletedBy; }

    /**
     * 设置删除者用户ID
     *
     * @param deletedBy 删除者用户ID
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
     * 获取最后更新时间
     *
     * @return 最后更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * 设置最后更新时间
     *
     * @param updatedAt 最后更新时间
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
     * @return 删除标记（0=未删除，其他=已删除）
     */
    public Long getDeleteFlag() { return deleteFlag; }

    /**
     * 设置删除标记
     *
     * @param deleteFlag 删除标记
     */
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}