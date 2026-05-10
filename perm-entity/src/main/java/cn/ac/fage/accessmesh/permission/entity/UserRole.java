package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 用户角色关系实体
 * <p>
 * 表示用户与角色之间的关联关系。
 * 支持多种关联目标类型（角色、组织、岗位等）。
 * 支持时间有效期控制（validFrom/validTo）。
 * 一个用户可以通过多种方式关联到角色。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("user_role")
public class UserRole {

    /**
     * 用户角色关系唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 抽象用户ID
     */
    private Long abstractUserId;

    /**
     * 目标类型（ROLE=角色，ORG=组织，POSITION=岗位）
     */
    private String targetType;

    /**
     * 目标ID（根据targetType确定具体含义）
     */
    private Long targetId;

    /**
     * 关系ID（如角色ID、组织ID、岗位ID等）
     */
    private Long relationId;

    /**
     * 有效起始时间
     */
    private LocalDateTime validFrom;

    /**
     * 有效结束时间
     */
    private LocalDateTime validTo;

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
     * 获取用户角色关系唯一标识
     *
     * @return 关系ID
     */
    public Long getId() { return id; }

    /**
     * 设置用户角色关系唯一标识
     *
     * @param id 关系ID
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
     * 获取抽象用户ID
     *
     * @return 抽象用户ID
     */
    public Long getAbstractUserId() { return abstractUserId; }

    /**
     * 设置抽象用户ID
     *
     * @param abstractUserId 抽象用户ID
     */
    public void setAbstractUserId(Long abstractUserId) { this.abstractUserId = abstractUserId; }

    /**
     * 获取目标类型
     *
     * @return 目标类型（ROLE=角色，ORG=组织，POSITION=岗位）
     */
    public String getTargetType() { return targetType; }

    /**
     * 设置目标类型
     *
     * @param targetType 目标类型
     */
    public void setTargetType(String targetType) { this.targetType = targetType; }

    /**
     * 获取目标ID
     *
     * @return 目标ID
     */
    public Long getTargetId() { return targetId; }

    /**
     * 设置目标ID
     *
     * @param targetId 目标ID
     */
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    /**
     * 获取关系ID
     *
     * @return 关系ID
     */
    public Long getRelationId() { return relationId; }

    /**
     * 设置关系ID
     *
     * @param relationId 关系ID
     */
    public void setRelationId(Long relationId) { this.relationId = relationId; }

    /**
     * 获取有效起始时间
     *
     * @return 有效起始时间
     */
    public LocalDateTime getValidFrom() { return validFrom; }

    /**
     * 设置有效起始时间
     *
     * @param validFrom 有效起始时间
     */
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    /**
     * 获取有效结束时间
     *
     * @return 有效结束时间
     */
    public LocalDateTime getValidTo() { return validTo; }

    /**
     * 设置有效结束时间
     *
     * @param validTo 有效结束时间
     */
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }

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