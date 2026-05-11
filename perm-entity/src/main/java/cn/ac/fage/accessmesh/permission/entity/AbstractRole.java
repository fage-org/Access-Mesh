package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 抽象角色实体
 * <p>
 * 表示系统中的角色主体，支持多种角色类型和层级结构。
 * 角色类型包括：全局角色、业务域角色、组织角色、岗位角色等。
 * 支持角色继承（通过parentId）和角色排序。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("abstract_role")
public class AbstractRole {

    /**
     * 角色唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 父角色ID，用于角色继承层级
     */
    private Long parentId;

    /**
     * 角色类型（0=全局角色，1=业务域角色，2=组织角色，3=岗位角色）
     */
    private Integer roleType;

    /**
     * 外部标识，用于关联外部系统角色
     */
    private String externalId;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 状态（0=禁用，1=启用）
     */
    private Integer status;

    /**
     * 排序顺序，用于角色列表展示排序
     */
    private Integer sortOrder;

    /**
     * 扩展信息（JSON格式），存储额外属性
     */
    private String extra;

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
     * 获取角色唯一标识
     *
     * @return 角色ID
     */
    public Long getId() { return id; }

    /**
     * 设置角色唯一标识
     *
     * @param id 角色ID
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
     * 获取父角色ID
     *
     * @return 父角色ID，用于角色继承层级
     */
    public Long getParentId() { return parentId; }

    /**
     * 设置父角色ID
     *
     * @param parentId 父角色ID
     */
    public void setParentId(Long parentId) { this.parentId = parentId; }

    /**
     * 获取角色类型
     *
     * @return 角色类型（0=全局角色，1=业务域角色，2=组织角色，3=岗位角色）
     */
    public Integer getRoleType() { return roleType; }

    /**
     * 设置角色类型
     *
     * @param roleType 角色类型
     */
    public void setRoleType(Integer roleType) { this.roleType = roleType; }

    /**
     * 获取外部标识
     *
     * @return 外部系统角色标识
     */
    public String getExternalId() { return externalId; }

    /**
     * 设置外部标识
     *
     * @param externalId 外部系统角色标识
     */
    public void setExternalId(String externalId) { this.externalId = externalId; }

    /**
     * 获取角色名称
     *
     * @return 角色名称
     */
    public String getName() { return name; }

    /**
     * 设置角色名称
     *
     * @param name 角色名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取状态
     *
     * @return 状态（0=禁用，1=启用）
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取排序顺序
     *
     * @return 排序顺序
     */
    public Integer getSortOrder() { return sortOrder; }

    /**
     * 设置排序顺序
     *
     * @param sortOrder 排序顺序
     */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 获取扩展信息
     *
     * @return 扩展信息（JSON格式）
     */
    public String getExtra() { return extra; }

    /**
     * 设置扩展信息
     *
     * @param extra 扩展信息（JSON格式）
     */
    public void setExtra(String extra) { this.extra = extra; }

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