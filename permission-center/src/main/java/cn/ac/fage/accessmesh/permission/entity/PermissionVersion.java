package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

@Table("permission_version")
public class PermissionVersion {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long abstractRoleId;
    private Long versionNo;
    private String triggerEntityType;
    private Long triggerEntityId;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getAbstractRoleId() { return abstractRoleId; }
    public void setAbstractRoleId(Long abstractRoleId) { this.abstractRoleId = abstractRoleId; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public String getTriggerEntityType() { return triggerEntityType; }
    public void setTriggerEntityType(String triggerEntityType) { this.triggerEntityType = triggerEntityType; }
    public Long getTriggerEntityId() { return triggerEntityId; }
    public void setTriggerEntityId(Long triggerEntityId) { this.triggerEntityId = triggerEntityId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
