package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 类型定义实体
 * <p>
 * 表示系统中各种类型的定义配置。
 * 支持资源类型、操作类型、角色类型等多种枚举定义。
 * 通过typeKey区分不同类型体系，typeCode和typeValue定义具体类型项。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("type_definition")
public class TypeDefinition {

    /**
     * 类型定义唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 类型键，标识类型所属的分类体系
     */
    private String typeKey;

    /**
     * 类型编码，用于标识具体类型
     */
    private String typeCode;

    /**
     * 类型值，用于存储类型的数值表示
     */
    private Integer typeValue;

    /**
     * 类型名称
     */
    private String name;

    /**
     * 类型描述，说明类型的含义和用途
     */
    private String description;

    /**
     * 是否系统类型（true=系统预置，不可删除；false=用户自定义）
     */
    private Boolean isSystem;

    /**
     * 排序顺序，用于类型列表展示排序
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
     * 获取类型定义唯一标识
     *
     * @return 类型定义ID
     */
    public Long getId() { return id; }

    /**
     * 设置类型定义唯一标识
     *
     * @param id 类型定义ID
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
     * 获取类型键
     *
     * @return 类型键
     */
    public String getTypeKey() { return typeKey; }

    /**
     * 设置类型键
     *
     * @param typeKey 类型键
     */
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }

    /**
     * 获取类型编码
     *
     * @return 类型编码
     */
    public String getTypeCode() { return typeCode; }

    /**
     * 设置类型编码
     *
     * @param typeCode 类型编码
     */
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }

    /**
     * 获取类型值
     *
     * @return 类型值
     */
    public Integer getTypeValue() { return typeValue; }

    /**
     * 设置类型值
     *
     * @param typeValue 类型值
     */
    public void setTypeValue(Integer typeValue) { this.typeValue = typeValue; }

    /**
     * 获取类型名称
     *
     * @return 类型名称
     */
    public String getName() { return name; }

    /**
     * 设置类型名称
     *
     * @param name 类型名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取类型描述
     *
     * @return 类型描述
     */
    public String getDescription() { return description; }

    /**
     * 设置类型描述
     *
     * @param description 类型描述
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取是否系统类型
     *
     * @return 是否系统类型（true=系统预置，false=用户自定义）
     */
    public Boolean getIsSystem() { return isSystem; }

    /**
     * 设置是否系统类型
     *
     * @param isSystem 是否系统类型
     */
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }

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