package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统菜单实体类
 * <p>
 * 对应数据库表sys_menu，用于存储系统菜单和权限配置。
 * 支持菜单树结构、路由配置、权限码绑定等。
 * </p>
 */
@Table("sys_menu")
public class SysMenu {

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
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 菜单类型（M=目录，C=菜单，F=按钮）
     */
    private String menuType;

    /**
     * 服务编码
     */
    private String serviceCode;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 权限编码
     */
    private String permCode;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 是否可见
     */
    private Boolean visible;

    /**
     * 是否外链
     */
    private Boolean isExternal;

    /**
     * 是否内嵌框架
     */
    private Boolean isFrame;

    /**
     * 是否缓存
     */
    private Boolean isCache;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 扩展配置（JSON格式）
     */
    private String extra;

    /**
     * 权限资源ID
     */
    private Long permResourceId;

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
     * 获取父菜单ID
     *
     * @return 父菜单ID
     */
    public Long getParentId() { return parentId; }

    /**
     * 设置父菜单ID
     *
     * @param parentId 父菜单ID
     */
    public void setParentId(Long parentId) { this.parentId = parentId; }

    /**
     * 获取菜单类型
     *
     * @return 菜单类型
     */
    public String getMenuType() { return menuType; }

    /**
     * 设置菜单类型
     *
     * @param menuType 菜单类型
     */
    public void setMenuType(String menuType) { this.menuType = menuType; }

    /**
     * 获取服务编码
     *
     * @return 服务编码
     */
    public String getServiceCode() { return serviceCode; }

    /**
     * 设置服务编码
     *
     * @param serviceCode 服务编码
     */
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    /**
     * 获取菜单名称
     *
     * @return 菜单名称
     */
    public String getName() { return name; }

    /**
     * 设置菜单名称
     *
     * @param name 菜单名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取路由路径
     *
     * @return 路由路径
     */
    public String getPath() { return path; }

    /**
     * 设置路由路径
     *
     * @param path 路由路径
     */
    public void setPath(String path) { this.path = path; }

    /**
     * 获取组件路径
     *
     * @return 组件路径
     */
    public String getComponent() { return component; }

    /**
     * 设置组件路径
     *
     * @param component 组件路径
     */
    public void setComponent(String component) { this.component = component; }

    /**
     * 获取菜单图标
     *
     * @return 菜单图标
     */
    public String getIcon() { return icon; }

    /**
     * 设置菜单图标
     *
     * @param icon 菜单图标
     */
    public void setIcon(String icon) { this.icon = icon; }

    /**
     * 获取权限编码
     *
     * @return 权限编码
     */
    public String getPermCode() { return permCode; }

    /**
     * 设置权限编码
     *
     * @param permCode 权限编码
     */
    public void setPermCode(String permCode) { this.permCode = permCode; }

    /**
     * 获取排序序号
     *
     * @return 排序序号
     */
    public Integer getSortOrder() { return sortOrder; }

    /**
     * 设置排序序号
     *
     * @param sortOrder 排序序号
     */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 获取是否可见
     *
     * @return 是否可见
     */
    public Boolean getVisible() { return visible; }

    /**
     * 设置是否可见
     *
     * @param visible 是否可见
     */
    public void setVisible(Boolean visible) { this.visible = visible; }

    /**
     * 获取是否外链
     *
     * @return 是否外链
     */
    public Boolean getIsExternal() { return isExternal; }

    /**
     * 设置是否外链
     *
     * @param isExternal 是否外链
     */
    public void setIsExternal(Boolean isExternal) { this.isExternal = isExternal; }

    /**
     * 获取是否内嵌框架
     *
     * @return 是否内嵌框架
     */
    public Boolean getIsFrame() { return isFrame; }

    /**
     * 设置是否内嵌框架
     *
     * @param isFrame 是否内嵌框架
     */
    public void setIsFrame(Boolean isFrame) { this.isFrame = isFrame; }

    /**
     * 获取是否缓存
     *
     * @return 是否缓存
     */
    public Boolean getIsCache() { return isCache; }

    /**
     * 设置是否缓存
     *
     * @param isCache 是否缓存
     */
    public void setIsCache(Boolean isCache) { this.isCache = isCache; }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取扩展配置
     *
     * @return 扩展配置
     */
    public String getExtra() { return extra; }

    /**
     * 设置扩展配置
     *
     * @param extra 扩展配置
     */
    public void setExtra(String extra) { this.extra = extra; }

    /**
     * 获取权限资源ID
     *
     * @return 权限资源ID
     */
    public Long getPermResourceId() { return permResourceId; }

    /**
     * 设置权限资源ID
     *
     * @param permResourceId 权限资源ID
     */
    public void setPermResourceId(Long permResourceId) { this.permResourceId = permResourceId; }

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