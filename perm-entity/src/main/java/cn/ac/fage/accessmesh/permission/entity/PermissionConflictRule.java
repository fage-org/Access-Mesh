package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 权限冲突规则实体
 * <p>
 * 表示权限冲突检测的规则配置。
 * 定义两种操作权限或角色之间的冲突关系，
 * 用于在授权时检测和防止权限冲突。
 * 冲突类型包括：互斥操作、角色互斥等。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("permission_conflict_rule")
public class PermissionConflictRule {

    /**
     * 权限冲突规则唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 所属业务域ID
     */
    private Long bizDomainId;

    /**
     * 冲突类型（MUTEX_OP=操作互斥，MUTEX_ROLE=角色互斥）
     */
    private String conflictType;

    /**
     * 第一个操作权限ID
     */
    private Long firstOperationPermissionId;

    /**
     * 第二个操作权限ID
     */
    private Long secondOperationPermissionId;

    /**
     * 资源类型值
     */
    private Integer resourceTypeValue;

    /**
     * 第一个抽象角色ID
     */
    private Long firstAbstractRoleId;

    /**
     * 第二个抽象角色ID
     */
    private Long secondAbstractRoleId;

    /**
     * 规则描述，说明冲突场景和处理方式
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
     * 获取权限冲突规则唯一标识
     *
     * @return 规则ID
     */
    public Long getId() { return id; }

    /**
     * 设置权限冲突规则唯一标识
     *
     * @param id 规则ID
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
     * 获取所属业务域ID
     *
     * @return 业务域ID
     */
    public Long getBizDomainId() { return bizDomainId; }

    /**
     * 设置所属业务域ID
     *
     * @param bizDomainId 业务域ID
     */
    public void setBizDomainId(Long bizDomainId) { this.bizDomainId = bizDomainId; }

    /**
     * 获取冲突类型
     *
     * @return 冲突类型（MUTEX_OP=操作互斥，MUTEX_ROLE=角色互斥）
     */
    public String getConflictType() { return conflictType; }

    /**
     * 设置冲突类型
     *
     * @param conflictType 冲突类型
     */
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }

    /**
     * 获取第一个操作权限ID
     *
     * @return 操作权限ID
     */
    public Long getFirstOperationPermissionId() { return firstOperationPermissionId; }

    /**
     * 设置第一个操作权限ID
     *
     * @param firstOperationPermissionId 操作权限ID
     */
    public void setFirstOperationPermissionId(Long firstOperationPermissionId) { this.firstOperationPermissionId = firstOperationPermissionId; }

    /**
     * 获取第二个操作权限ID
     *
     * @return 操作权限ID
     */
    public Long getSecondOperationPermissionId() { return secondOperationPermissionId; }

    /**
     * 设置第二个操作权限ID
     *
     * @param secondOperationPermissionId 操作权限ID
     */
    public void setSecondOperationPermissionId(Long secondOperationPermissionId) { this.secondOperationPermissionId = secondOperationPermissionId; }

    /**
     * 获取资源类型值
     *
     * @return 资源类型值
     */
    public Integer getResourceTypeValue() { return resourceTypeValue; }

    /**
     * 设置资源类型值
     *
     * @param resourceTypeValue 资源类型值
     */
    public void setResourceTypeValue(Integer resourceTypeValue) { this.resourceTypeValue = resourceTypeValue; }

    /**
     * 获取第一个抽象角色ID
     *
     * @return 抽象角色ID
     */
    public Long getFirstAbstractRoleId() { return firstAbstractRoleId; }

    /**
     * 设置第一个抽象角色ID
     *
     * @param firstAbstractRoleId 抽象角色ID
     */
    public void setFirstAbstractRoleId(Long firstAbstractRoleId) { this.firstAbstractRoleId = firstAbstractRoleId; }

    /**
     * 获取第二个抽象角色ID
     *
     * @return 抽象角色ID
     */
    public Long getSecondAbstractRoleId() { return secondAbstractRoleId; }

    /**
     * 设置第二个抽象角色ID
     *
     * @param secondAbstractRoleId 抽象角色ID
     */
    public void setSecondAbstractRoleId(Long secondAbstractRoleId) { this.secondAbstractRoleId = secondAbstractRoleId; }

    /**
     * 获取规则描述
     *
     * @return 规则描述
     */
    public String getDescription() { return description; }

    /**
     * 设置规则描述
     *
     * @param description 规则描述
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