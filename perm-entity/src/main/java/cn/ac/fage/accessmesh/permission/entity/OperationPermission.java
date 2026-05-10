package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 操作权限实体
 * <p>
 * 表示对资源可执行的操作类型，采用位掩码设计。
 * 每种操作对应一个二进制位（binaryBit），支持操作的继承（inheritMask）。
 * 常见操作包括：查看(VIEW)、创建(CREATE)、编辑(EDIT)、删除(DELETE)、管理(MANAGE)等。
 * 通过位运算可高效判断权限包含关系。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("operation_permission")
public class OperationPermission {

    /**
     * 操作权限唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 资源类型
     */
    private Integer resourceType;

    /**
     * 操作编码，用于标识操作类型
     */
    private String code;

    /**
     * 操作名称
     */
    private String name;

    /**
     * 二进制位值，用于位运算判断
     */
    private Long binaryBit;

    /**
     * 继承掩码，表示该操作隐含的其他操作权限
     */
    private Long inheritMask;

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
     * 获取操作权限唯一标识
     *
     * @return 操作权限ID
     */
    public Long getId() { return id; }

    /**
     * 设置操作权限唯一标识
     *
     * @param id 操作权限ID
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
     * 获取资源类型
     *
     * @return 资源类型
     */
    public Integer getResourceType() { return resourceType; }

    /**
     * 设置资源类型
     *
     * @param resourceType 资源类型
     */
    public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }

    /**
     * 获取操作编码
     *
     * @return 操作编码
     */
    public String getCode() { return code; }

    /**
     * 设置操作编码
     *
     * @param code 操作编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取操作名称
     *
     * @return 操作名称
     */
    public String getName() { return name; }

    /**
     * 设置操作名称
     *
     * @param name 操作名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取二进制位值
     *
     * @return 二进制位值，用于位运算判断
     */
    public Long getBinaryBit() { return binaryBit; }

    /**
     * 设置二进制位值
     *
     * @param binaryBit 二进制位值
     */
    public void setBinaryBit(Long binaryBit) { this.binaryBit = binaryBit; }

    /**
     * 获取继承掩码
     *
     * @return 继承掩码，表示该操作隐含的其他操作权限
     */
    public Long getInheritMask() { return inheritMask; }

    /**
     * 设置继承掩码
     *
     * @param inheritMask 继承掩码
     */
    public void setInheritMask(Long inheritMask) { this.inheritMask = inheritMask; }

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

    /**
     * 获取有效权限位值
     * <p>
     * 计算公式：binaryBit | inheritMask。
     * 用于权限匹配的位运算判断。
     * </p>
     *
     * @return 有效权限位值
     */
    public long getEffectiveBits() {
        return (binaryBit != null ? binaryBit : 0L)
            | (inheritMask != null ? inheritMask : 0L);
    }

    /**
     * 判断权限位是否匹配目标操作
     * <p>
     * 用于判断当前权限是否包含目标操作权限。
     * 判断逻辑：(effectiveBits & target.binaryBit) != 0。
     * </p>
     *
     * @param target 目标操作权限
     * @return 如果匹配返回true，否则返回false
     */
    public boolean matchesBit(OperationPermission target) {
        if (target == null || target.binaryBit == null || target.binaryBit == 0L) {
            return false;
        }
        return (getEffectiveBits() & target.binaryBit) != 0;
    }
}