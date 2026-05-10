package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统组织实体类
 * <p>
 * 对应数据库表sys_org，用于存储组织机构信息。
 * 支持树形组织结构、组织类型、组织编码、权限关联等。
 * </p>
 */
@Table("sys_org")
public class SysOrg {

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
     * 父组织ID
     */
    private Long parentId;

    /**
     * 组织类型
     */
    private String orgType;

    /**
     * 组织编码
     */
    private String code;

    /**
     * 组织名称
     */
    private String name;

    /**
     * 组织路径（祖先ID链）
     */
    private String path;

    /**
     * 组织层级（深度）
     */
    private Integer level;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 组织负责人ID
     */
    private Long leaderId;

    /**
     * 状态（0=正常，1=禁用）
     */
    private Integer status;

    /**
     * 权限角色ID（组织对应角色）
     */
    private Long permRoleId;

    /**
     * 权限组织ID（对应权限中心的组织）
     */
    private Long permOrgId;

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
     * 获取父组织ID
     *
     * @return 父组织ID
     */
    public Long getParentId() { return parentId; }

    /**
     * 设置父组织ID
     *
     * @param parentId 父组织ID
     */
    public void setParentId(Long parentId) { this.parentId = parentId; }

    /**
     * 获取组织类型
     *
     * @return 组织类型
     */
    public String getOrgType() { return orgType; }

    /**
     * 设置组织类型
     *
     * @param orgType 组织类型
     */
    public void setOrgType(String orgType) { this.orgType = orgType; }

    /**
     * 获取组织编码
     *
     * @return 组织编码
     */
    public String getCode() { return code; }

    /**
     * 设置组织编码
     *
     * @param code 组织编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取组织名称
     *
     * @return 组织名称
     */
    public String getName() { return name; }

    /**
     * 设置组织名称
     *
     * @param name 组织名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取组织路径
     *
     * @return 组织路径
     */
    public String getPath() { return path; }

    /**
     * 设置组织路径
     *
     * @param path 组织路径
     */
    public void setPath(String path) { this.path = path; }

    /**
     * 获取组织层级
     *
     * @return 组织层级
     */
    public Integer getLevel() { return level; }

    /**
     * 设置组织层级
     *
     * @param level 组织层级
     */
    public void setLevel(Integer level) { this.level = level; }

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
     * 获取组织负责人ID
     *
     * @return 组织负责人ID
     */
    public Long getLeaderId() { return leaderId; }

    /**
     * 设置组织负责人ID
     *
     * @param leaderId 组织负责人ID
     */
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }

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
     * 获取权限角色ID
     *
     * @return 权限角色ID
     */
    public Long getPermRoleId() { return permRoleId; }

    /**
     * 设置权限角色ID
     *
     * @param permRoleId 权限角色ID
     */
    public void setPermRoleId(Long permRoleId) { this.permRoleId = permRoleId; }

    /**
     * 获取权限组织ID
     *
     * @return 权限组织ID
     */
    public Long getPermOrgId() { return permOrgId; }

    /**
     * 设置权限组织ID
     *
     * @param permOrgId 权限组织ID
     */
    public void setPermOrgId(Long permOrgId) { this.permOrgId = permOrgId; }

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