package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 组织树配置实体类
 * <p>
 * 对应数据库表sys_org_tree_config，用于存储组织树配置。
 * 支持多种组织树类型、默认树设置、单关联配置等。
 * </p>
 */
@Table("sys_org_tree_config")
public class SysOrgTreeConfig {

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
     * 根组织ID
     */
    private Long rootOrgId;

    /**
     * 组织树名称
     */
    private String treeName;

    /**
     * 组织树类型
     */
    private String treeType;

    /**
     * 是否默认树
     */
    private Boolean isDefault;

    /**
     * 是否单关联（用户只能属于一个节点）
     */
    private Boolean singleAssoc;

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
     * 获取根组织ID
     *
     * @return 根组织ID
     */
    public Long getRootOrgId() { return rootOrgId; }

    /**
     * 设置根组织ID
     *
     * @param rootOrgId 根组织ID
     */
    public void setRootOrgId(Long rootOrgId) { this.rootOrgId = rootOrgId; }

    /**
     * 获取组织树名称
     *
     * @return 组织树名称
     */
    public String getTreeName() { return treeName; }

    /**
     * 设置组织树名称
     *
     * @param treeName 组织树名称
     */
    public void setTreeName(String treeName) { this.treeName = treeName; }

    /**
     * 获取组织树类型
     *
     * @return 组织树类型
     */
    public String getTreeType() { return treeType; }

    /**
     * 设置组织树类型
     *
     * @param treeType 组织树类型
     */
    public void setTreeType(String treeType) { this.treeType = treeType; }

    /**
     * 获取是否默认树
     *
     * @return 是否默认树
     */
    public Boolean getIsDefault() { return isDefault; }

    /**
     * 设置是否默认树
     *
     * @param isDefault 是否默认树
     */
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    /**
     * 获取是否单关联
     *
     * @return 是否单关联
     */
    public Boolean getSingleAssoc() { return singleAssoc; }

    /**
     * 设置是否单关联
     *
     * @param singleAssoc 是否单关联
     */
    public void setSingleAssoc(Boolean singleAssoc) { this.singleAssoc = singleAssoc; }

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