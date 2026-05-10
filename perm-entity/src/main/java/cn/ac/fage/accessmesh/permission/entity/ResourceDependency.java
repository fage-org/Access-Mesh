package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 资源依赖关系实体
 * <p>
 * 表示资源之间的权限依赖关系。
 * 定义某个资源的权限依赖于另一个资源的权限。
 * 支持权限自动授予（autoGrant）和权限位映射（operationBits）。
 * 用于实现权限的级联授予和依赖管理。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("resource_dependency")
public class ResourceDependency {

    /**
     * 资源依赖关系唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 资源实体ID（依赖方）
     */
    private Long resourceEntityId;

    /**
     * 依赖的资源实体ID（被依赖方）
     */
    private Long dependsOnResourceEntityId;

    /**
     * 源操作位值，触发依赖的操作权限位
     */
    private Long sourceOperationBits;

    /**
     * 需要的操作位值，依赖关系需要授予的权限位
     */
    private Long requiredOperationBits;

    /**
     * 是否自动授予（true=自动授予依赖权限，false=不自动）
     */
    private Boolean autoGrant;

    /**
     * 依赖关系描述
     */
    private String description;

    /**
     * 所属服务编码
     */
    private String ownerServiceCode;

    /**
     * 维护来源（MANUAL/SYNC）
     */
    private String maintainSource;

    /**
     * 同步键，用于外部系统同步
     */
    private String syncKey;

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
     * 获取资源依赖关系唯一标识
     *
     * @return 依赖关系ID
     */
    public Long getId() { return id; }

    /**
     * 设置资源依赖关系唯一标识
     *
     * @param id 依赖关系ID
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
     * 获取资源实体ID
     *
     * @return 资源实体ID（依赖方）
     */
    public Long getResourceEntityId() { return resourceEntityId; }

    /**
     * 设置资源实体ID
     *
     * @param resourceEntityId 资源实体ID
     */
    public void setResourceEntityId(Long resourceEntityId) { this.resourceEntityId = resourceEntityId; }

    /**
     * 获取依赖的资源实体ID
     *
     * @return 依赖的资源实体ID（被依赖方）
     */
    public Long getDependsOnResourceEntityId() { return dependsOnResourceEntityId; }

    /**
     * 设置依赖的资源实体ID
     *
     * @param dependsOnResourceEntityId 依赖的资源实体ID
     */
    public void setDependsOnResourceEntityId(Long dependsOnResourceEntityId) { this.dependsOnResourceEntityId = dependsOnResourceEntityId; }

    /**
     * 获取源操作位值
     *
     * @return 源操作位值
     */
    public Long getSourceOperationBits() { return sourceOperationBits; }

    /**
     * 设置源操作位值
     *
     * @param sourceOperationBits 源操作位值
     */
    public void setSourceOperationBits(Long sourceOperationBits) { this.sourceOperationBits = sourceOperationBits; }

    /**
     * 获取需要的操作位值
     *
     * @return 需要的操作位值
     */
    public Long getRequiredOperationBits() { return requiredOperationBits; }

    /**
     * 设置需要的操作位值
     *
     * @param requiredOperationBits 需要的操作位值
     */
    public void setRequiredOperationBits(Long requiredOperationBits) { this.requiredOperationBits = requiredOperationBits; }

    /**
     * 获取是否自动授予
     *
     * @return 是否自动授予（true=自动授予，false=不自动）
     */
    public Boolean getAutoGrant() { return autoGrant; }

    /**
     * 设置是否自动授予
     *
     * @param autoGrant 是否自动授予
     */
    public void setAutoGrant(Boolean autoGrant) { this.autoGrant = autoGrant; }

    /**
     * 获取依赖关系描述
     *
     * @return 依赖关系描述
     */
    public String getDescription() { return description; }

    /**
     * 设置依赖关系描述
     *
     * @param description 依赖关系描述
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取所属服务编码
     *
     * @return 所属服务编码
     */
    public String getOwnerServiceCode() { return ownerServiceCode; }

    /**
     * 设置所属服务编码
     *
     * @param ownerServiceCode 所属服务编码
     */
    public void setOwnerServiceCode(String ownerServiceCode) { this.ownerServiceCode = ownerServiceCode; }

    /**
     * 获取维护来源
     *
     * @return 维护来源（MANUAL/SYNC）
     */
    public String getMaintainSource() { return maintainSource; }

    /**
     * 设置维护来源
     *
     * @param maintainSource 维护来源
     */
    public void setMaintainSource(String maintainSource) { this.maintainSource = maintainSource; }

    /**
     * 获取同步键
     *
     * @return 同步键
     */
    public String getSyncKey() { return syncKey; }

    /**
     * 设置同步键
     *
     * @param syncKey 同步键
     */
    public void setSyncKey(String syncKey) { this.syncKey = syncKey; }

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