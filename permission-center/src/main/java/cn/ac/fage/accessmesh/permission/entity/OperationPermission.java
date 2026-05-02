package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("operation_permission")
public class OperationPermission {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Integer resourceType;
    private String code;
    private String name;
    private Long binaryBit;
    private Long inheritMask;
    private Long createdBy;
    private Long updatedBy;
    private Long deletedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deleteFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Integer getResourceType() { return resourceType; }
    public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getBinaryBit() { return binaryBit; }
    public void setBinaryBit(Long binaryBit) { this.binaryBit = binaryBit; }
    public Long getInheritMask() { return inheritMask; }
    public void setInheritMask(Long inheritMask) { this.inheritMask = inheritMask; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Long getDeleteFlag() { return deleteFlag; }
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }

    /**
     * Get effective permission bits: binaryBit | inheritMask.
     * Used for permission matching with bitwise operations.
     */
    public long getEffectiveBits() {
        return (binaryBit != null ? binaryBit : 0L)
            | (inheritMask != null ? inheritMask : 0L);
    }

    /**
     * Check if this permission's effective bits match the target's binary bit.
     * Used to determine if a permission grant covers the requested operation.
     *
     * @param target the target operation permission to match against
     * @return true if (effectiveBits & target.binaryBit) != 0
     */
    public boolean matchesBit(OperationPermission target) {
        if (target == null || target.binaryBit == null || target.binaryBit == 0L) {
            return false;
        }
        return (getEffectiveBits() & target.binaryBit) != 0;
    }
}
