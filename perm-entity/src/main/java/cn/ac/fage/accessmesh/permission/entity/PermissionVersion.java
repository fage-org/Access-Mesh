package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 权限版本实体
 * <p>
 * 表示角色权限的版本号记录。
 * 每次角色权限变更时版本号递增，用于缓存失效判断。
 * 通过版本号机制确保分布式环境下权限缓存的一致性。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("permission_version")
public class PermissionVersion {

    /**
     * 权限版本唯一标识
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
     * 版本号，每次变更递增
     */
    private Long versionNo;

    /**
     * 触发实体类型（ROLE/RESOURCE/PERMISSION等）
     */
    private String triggerEntityType;

    /**
     * 触发实体ID
     */
    private Long triggerEntityId;

    /**
     * 备注，说明版本变更原因
     */
    private String remark;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 获取权限版本唯一标识
     *
     * @return 版本ID
     */
    public Long getId() { return id; }

    /**
     * 设置权限版本唯一标识
     *
     * @param id 版本ID
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
     * 获取版本号
     *
     * @return 版本号，每次变更递增
     */
    public Long getVersionNo() { return versionNo; }

    /**
     * 设置版本号
     *
     * @param versionNo 版本号
     */
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }

    /**
     * 获取触发实体类型
     *
     * @return 触发实体类型（ROLE/RESOURCE/PERMISSION等）
     */
    public String getTriggerEntityType() { return triggerEntityType; }

    /**
     * 设置触发实体类型
     *
     * @param triggerEntityType 触发实体类型
     */
    public void setTriggerEntityType(String triggerEntityType) { this.triggerEntityType = triggerEntityType; }

    /**
     * 获取触发实体ID
     *
     * @return 触发实体ID
     */
    public Long getTriggerEntityId() { return triggerEntityId; }

    /**
     * 设置触发实体ID
     *
     * @param triggerEntityId 触发实体ID
     */
    public void setTriggerEntityId(Long triggerEntityId) { this.triggerEntityId = triggerEntityId; }

    /**
     * 获取备注
     *
     * @return 备注
     */
    public String getRemark() { return remark; }

    /**
     * 设置备注
     *
     * @param remark 备注
     */
    public void setRemark(String remark) { this.remark = remark; }

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
}