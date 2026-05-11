package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 资源实体
 * <p>
 * 表示系统中需要权限控制的资源对象。
 * 资源类型包括：菜单、按钮、API、数据等。
 * 支持资源层级结构（通过parentId）和资源路径定位。
 * 资源可由外部服务同步维护（通过maintainSource和syncKey）。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("resource_entity")
public class ResourceEntity {

    /**
     * 资源唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 父资源ID，用于资源层级结构
     */
    private Long parentId;

    /**
     * 资源类型（0=菜单，1=按钮，2=API，3=数据）
     */
    private Integer resourceType;

    /**
     * 资源编码，用于权限标识
     */
    private String code;

    /**
     * 编码类型（用于区分不同编码体系）
     */
    private String codeType;

    /**
     * 资源名称
     */
    private String name;

    /**
     * 资源路径，用于定位资源位置
     */
    private String path;

    /**
     * 状态（0=禁用，1=启用）
     */
    private Integer status;

    /**
     * 排序顺序，用于资源列表展示排序
     */
    private Integer sortOrder;

    /**
     * 所属服务编码，标识资源所属的服务
     */
    private String ownerServiceCode;

    /**
     * 维护来源，标识资源的维护方式（MANUAL/SYNC）
     */
    private String maintainSource;

    /**
     * 同步键，用于外部系统同步时的唯一标识
     */
    private String syncKey;

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
     * 获取资源唯一标识
     *
     * @return 资源ID
     */
    public Long getId() { return id; }

    /**
     * 设置资源唯一标识
     *
     * @param id 资源ID
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
     * 获取父资源ID
     *
     * @return 父资源ID，用于资源层级结构
     */
    public Long getParentId() { return parentId; }

    /**
     * 设置父资源ID
     *
     * @param parentId 父资源ID
     */
    public void setParentId(Long parentId) { this.parentId = parentId; }

    /**
     * 获取资源类型
     *
     * @return 资源类型（0=菜单，1=按钮，2=API，3=数据）
     */
    public Integer getResourceType() { return resourceType; }

    /**
     * 设置资源类型
     *
     * @param resourceType 资源类型
     */
    public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }

    /**
     * 获取资源编码
     *
     * @return 资源编码，用于权限标识
     */
    public String getCode() { return code; }

    /**
     * 设置资源编码
     *
     * @param code 资源编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取编码类型
     *
     * @return 编码类型
     */
    public String getCodeType() { return codeType; }

    /**
     * 设置编码类型
     *
     * @param codeType 编码类型
     */
    public void setCodeType(String codeType) { this.codeType = codeType; }

    /**
     * 获取资源名称
     *
     * @return 资源名称
     */
    public String getName() { return name; }

    /**
     * 设置资源名称
     *
     * @param name 资源名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取资源路径
     *
     * @return 资源路径
     */
    public String getPath() { return path; }

    /**
     * 设置资源路径
     *
     * @param path 资源路径
     */
    public void setPath(String path) { this.path = path; }

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
     * @return 同步键，用于外部系统同步
     */
    public String getSyncKey() { return syncKey; }

    /**
     * 设置同步键
     *
     * @param syncKey 同步键
     */
    public void setSyncKey(String syncKey) { this.syncKey = syncKey; }

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