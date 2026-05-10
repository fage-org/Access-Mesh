package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 权限条件实体
 * <p>
 * 表示动态权限判断的条件规则配置。
 * 条件规则以JSON格式存储，支持复杂的条件表达式。
 * 可用于实现基于时间、组织、数据属性等的动态权限控制。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("permission_condition")
public class PermissionCondition {

    /**
     * 权限条件唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 条件编码，用于标识和引用
     */
    private String code;

    /**
     * 条件名称
     */
    private String name;

    /**
     * 条件规则（JSON格式），定义具体的条件逻辑
     */
    private String conditionRules;

    /**
     * 启用状态（true=启用，false=禁用）
     */
    private Boolean enabled;

    /**
     * 条件描述，说明条件用途和效果
     */
    private String description;

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
     * 获取权限条件唯一标识
     *
     * @return 条件ID
     */
    public Long getId() { return id; }

    /**
     * 设置权限条件唯一标识
     *
     * @param id 条件ID
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
     * 获取条件编码
     *
     * @return 条件编码
     */
    public String getCode() { return code; }

    /**
     * 设置条件编码
     *
     * @param code 条件编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取条件名称
     *
     * @return 条件名称
     */
    public String getName() { return name; }

    /**
     * 设置条件名称
     *
     * @param name 条件名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取条件规则
     *
     * @return 条件规则（JSON格式）
     */
    public String getConditionRules() { return conditionRules; }

    /**
     * 设置条件规则
     *
     * @param conditionRules 条件规则（JSON格式）
     */
    public void setConditionRules(String conditionRules) { this.conditionRules = conditionRules; }

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
     * 获取条件描述
     *
     * @return 条件描述
     */
    public String getDescription() { return description; }

    /**
     * 设置条件描述
     *
     * @param description 条件描述
     */
    public void setDescription(String description) { this.description = description; }

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