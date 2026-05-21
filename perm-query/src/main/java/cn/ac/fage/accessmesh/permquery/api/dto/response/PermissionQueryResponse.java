package cn.ac.fage.accessmesh.permquery.api.dto.response;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.ConflictInfo;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限查询响应
 * <p>
 * 返回完整权限数据，调用方自行判断决策。
 * </p>
 */
public class PermissionQueryResponse {

    // 权限条目
    private List<GrantedPermissionItem> scopeAllPermissions;
    private List<GrantedPermissionItem> instancePermissions;

    // 辅助信息
    private Map<Long, RoleInfo> roles;
    private Map<Long, ResourceInfo> resources;
    private Map<Long, OperationInfo> operations;
    private Map<Long, ConditionInfo> conditions;

    // 评估结果
    private Map<Long, Boolean> conditionResults;
    private List<ConflictInfoItem> conflicts;

    // 查询上下文
    private Set<Long> resolvedRoleIds;
    private Set<Integer> resolvedResourceTypes;

    public List<GrantedPermissionItem> getScopeAllPermissions() {
        return scopeAllPermissions;
    }

    public void setScopeAllPermissions(List<GrantedPermissionItem> scopeAllPermissions) {
        this.scopeAllPermissions = scopeAllPermissions;
    }

    public List<GrantedPermissionItem> getInstancePermissions() {
        return instancePermissions;
    }

    public void setInstancePermissions(List<GrantedPermissionItem> instancePermissions) {
        this.instancePermissions = instancePermissions;
    }

    public Map<Long, RoleInfo> getRoles() {
        return roles;
    }

    public void setRoles(Map<Long, RoleInfo> roles) {
        this.roles = roles;
    }

    public Map<Long, ResourceInfo> getResources() {
        return resources;
    }

    public void setResources(Map<Long, ResourceInfo> resources) {
        this.resources = resources;
    }

    public Map<Long, OperationInfo> getOperations() {
        return operations;
    }

    public void setOperations(Map<Long, OperationInfo> operations) {
        this.operations = operations;
    }

    public Map<Long, ConditionInfo> getConditions() {
        return conditions;
    }

    public void setConditions(Map<Long, ConditionInfo> conditions) {
        this.conditions = conditions;
    }

    public Map<Long, Boolean> getConditionResults() {
        return conditionResults;
    }

    public void setConditionResults(Map<Long, Boolean> conditionResults) {
        this.conditionResults = conditionResults;
    }

    public List<ConflictInfoItem> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<ConflictInfoItem> conflicts) {
        this.conflicts = conflicts;
    }

    public Set<Long> getResolvedRoleIds() {
        return resolvedRoleIds;
    }

    public void setResolvedRoleIds(Set<Long> resolvedRoleIds) {
        this.resolvedRoleIds = resolvedRoleIds;
    }

    public Set<Integer> getResolvedResourceTypes() {
        return resolvedResourceTypes;
    }

    public void setResolvedResourceTypes(Set<Integer> resolvedResourceTypes) {
        this.resolvedResourceTypes = resolvedResourceTypes;
    }

    /**
     * 权限条目DTO
     */
    public static class GrantedPermissionItem {
        private Long permissionId;
        private Long roleId;
        private Integer resourceType;
        private Long resourceEntityId;
        private String operationCode;
        private Long grantedBits;
        private Long effectiveBits;
        private String grantSource;
        private Boolean canGrant;
        private Long conditionId;
        private Long dependOn;
        private Boolean conditionMet;
        private Boolean hasConflict;

        public static GrantedPermissionItem from(GrantedPermission p) {
            GrantedPermissionItem item = new GrantedPermissionItem();
            item.permissionId = p.permissionId();
            item.roleId = p.roleId();
            item.resourceType = p.resourceType();
            item.resourceEntityId = p.resourceEntityId();
            item.operationCode = p.operationCode();
            item.grantedBits = p.grantedBits();
            item.effectiveBits = p.effectiveBits();
            item.grantSource = p.grantSource();
            item.canGrant = p.canGrant();
            item.conditionId = p.conditionId();
            item.dependOn = p.dependOn();
            item.conditionMet = p.conditionMet();
            item.hasConflict = p.hasConflict();
            return item;
        }

        public Long getPermissionId() { return permissionId; }
        public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public Integer getResourceType() { return resourceType; }
        public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }
        public Long getResourceEntityId() { return resourceEntityId; }
        public void setResourceEntityId(Long resourceEntityId) { this.resourceEntityId = resourceEntityId; }
        public String getOperationCode() { return operationCode; }
        public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
        public Long getGrantedBits() { return grantedBits; }
        public void setGrantedBits(Long grantedBits) { this.grantedBits = grantedBits; }
        public Long getEffectiveBits() { return effectiveBits; }
        public void setEffectiveBits(Long effectiveBits) { this.effectiveBits = effectiveBits; }
        public String getGrantSource() { return grantSource; }
        public void setGrantSource(String grantSource) { this.grantSource = grantSource; }
        public Boolean getCanGrant() { return canGrant; }
        public void setCanGrant(Boolean canGrant) { this.canGrant = canGrant; }
        public Long getConditionId() { return conditionId; }
        public void setConditionId(Long conditionId) { this.conditionId = conditionId; }
        public Long getDependOn() { return dependOn; }
        public void setDependOn(Long dependOn) { this.dependOn = dependOn; }
        public Boolean getConditionMet() { return conditionMet; }
        public void setConditionMet(Boolean conditionMet) { this.conditionMet = conditionMet; }
        public Boolean getHasConflict() { return hasConflict; }
        public void setHasConflict(Boolean hasConflict) { this.hasConflict = hasConflict; }
    }

    /**
     * 冲突信息DTO
     */
    public static class ConflictInfoItem {
        private Long firstPermissionId;
        private Long secondPermissionId;
        private String conflictType;

        public static ConflictInfoItem from(ConflictInfo c) {
            ConflictInfoItem item = new ConflictInfoItem();
            item.firstPermissionId = c.firstPermissionId();
            item.secondPermissionId = c.secondPermissionId();
            item.conflictType = c.conflictType();
            return item;
        }

        public Long getFirstPermissionId() { return firstPermissionId; }
        public void setFirstPermissionId(Long firstPermissionId) { this.firstPermissionId = firstPermissionId; }
        public Long getSecondPermissionId() { return secondPermissionId; }
        public void setSecondPermissionId(Long secondPermissionId) { this.secondPermissionId = secondPermissionId; }
        public String getConflictType() { return conflictType; }
        public void setConflictType(String conflictType) { this.conflictType = conflictType; }
    }

    /**
     * 角色信息DTO
     */
    public static class RoleInfo {
        private Long id;
        private String name;
        private String code;
        private String roleType;

        public static RoleInfo from(AbstractRole r) {
            RoleInfo info = new RoleInfo();
            info.id = r.getId();
            info.name = r.getName();
            info.code = r.getCode();
            info.roleType = r.getRoleType();
            return info;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getRoleType() { return roleType; }
        public void setRoleType(String roleType) { this.roleType = roleType; }
    }

    /**
     * 资源信息DTO
     */
    public static class ResourceInfo {
        private Long id;
        private Integer resourceType;
        private String code;
        private String name;

        public static ResourceInfo from(ResourceEntity r) {
            ResourceInfo info = new ResourceInfo();
            info.id = r.getId();
            info.resourceType = r.getResourceType();
            info.code = r.getCode();
            info.name = r.getName();
            return info;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getResourceType() { return resourceType; }
        public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * 操作信息DTO
     */
    public static class OperationInfo {
        private Long id;
        private Integer resourceType;
        private String code;
        private String name;
        private Long binaryBit;

        public static OperationInfo from(OperationPermission o) {
            OperationInfo info = new OperationInfo();
            info.id = o.getId();
            info.resourceType = o.getResourceType();
            info.code = o.getCode();
            info.name = o.getName();
            info.binaryBit = o.getBinaryBit();
            return info;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getResourceType() { return resourceType; }
        public void setResourceType(Integer resourceType) { this.resourceType = resourceType; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getBinaryBit() { return binaryBit; }
        public void setBinaryBit(Long binaryBit) { this.binaryBit = binaryBit; }
    }

    /**
     * 条件信息DTO
     */
    public static class ConditionInfo {
        private Long id;
        private String name;
        private Boolean enabled;

        public static ConditionInfo from(PermissionCondition c) {
            ConditionInfo info = new ConditionInfo();
            info.id = c.getId();
            info.name = c.getName();
            info.enabled = c.getEnabled();
            return info;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}