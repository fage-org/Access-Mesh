package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 抽象用户实体
 * <p>
 * 表示系统中的用户主体，支持多种用户类型。
 * 用户类型包括：系统用户、组织用户、外部用户等。
 * 采用抽象设计，不直接存储认证信息，认证信息由外部服务管理。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("abstract_user")
public class AbstractUser {

    /**
     * 用户唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 用户类型（0=系统用户，1=组织用户，2=外部用户）
     */
    private Integer userType;

    /**
     * 外部标识，用于关联外部系统用户
     */
    private String externalId;

    /**
     * 用户名称
     */
    private String name;

    /**
     * 启用状态（true=启用，false=禁用）
     */
    private Boolean enabled;

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
     * 获取用户唯一标识
     *
     * @return 用户ID
     */
    public Long getId() { return id; }

    /**
     * 设置用户唯一标识
     *
     * @param id 用户ID
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
     * 获取用户类型
     *
     * @return 用户类型（0=系统用户，1=组织用户，2=外部用户）
     */
    public Integer getUserType() { return userType; }

    /**
     * 设置用户类型
     *
     * @param userType 用户类型
     */
    public void setUserType(Integer userType) { this.userType = userType; }

    /**
     * 获取外部标识
     *
     * @return 外部系统用户标识
     */
    public String getExternalId() { return externalId; }

    /**
     * 设置外部标识
     *
     * @param externalId 外部系统用户标识
     */
    public void setExternalId(String externalId) { this.externalId = externalId; }

    /**
     * 获取用户名称
     *
     * @return 用户名称
     */
    public String getName() { return name; }

    /**
     * 设置用户名称
     *
     * @param name 用户名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取启用状态
     *
     * @return 启用状态（true=启用，false=禁用）
     */
    public Boolean getEnabled() { return enabled; }

    /**
     * 设置启用状态
     *
     * @param enabled 启用状态
     */
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

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