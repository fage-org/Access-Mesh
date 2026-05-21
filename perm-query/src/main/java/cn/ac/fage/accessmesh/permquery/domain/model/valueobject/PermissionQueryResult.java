package cn.ac.fage.accessmesh.permquery.domain.model.valueobject;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 权限查询结果值对象
 * <p>
 * 返回原始权限数据，调用方自行判断决策。
 * 包含权限条目、角色信息、资源信息、操作信息、条件信息、评估结果等。
 * </p>
 */
public record PermissionQueryResult(
    List<GrantedPermission> scopeAllPermissions,
    List<GrantedPermission> instancePermissions,
    Map<Long, AbstractRole> roles,
    Map<Long, ResourceEntity> resources,
    Map<Long, OperationPermission> operations,
    Map<Long, PermissionCondition> conditions,
    Map<Long, Boolean> conditionResults,
    List<ConflictInfo> conflicts,
    Long tenantId,
    Set<Long> resolvedRoleIds,
    Set<Integer> resolvedResourceTypes
) {

    // ===== 便捷方法 =====

    /**
     * 合并所有权限条目
     */
    public List<GrantedPermission> allPermissions() {
        return Stream.concat(
            scopeAllPermissions != null ? scopeAllPermissions.stream() : Stream.empty(),
            instancePermissions != null ? instancePermissions.stream() : Stream.empty()
        ).toList();
    }

    /**
     * 获取所有权限ID
     */
    public Set<Long> permissionIds() {
        return allPermissions().stream()
            .map(GrantedPermission::permissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * 获取所有角色ID
     */
    public Set<Long> roleIds() {
        return allPermissions().stream()
            .map(GrantedPermission::roleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * 判断是否有类型级权限
     */
    public boolean hasScopeAllPermission() {
        return scopeAllPermissions != null && !scopeAllPermissions.isEmpty();
    }

    /**
     * 判断是否有实例级权限
     */
    public boolean hasInstancePermission() {
        return instancePermissions != null && !instancePermissions.isEmpty();
    }

    /**
     * 判断是否有任何权限
     */
    public boolean hasAnyPermission() {
        return hasScopeAllPermission() || hasInstancePermission();
    }

    /**
     * 判断是否有冲突
     */
    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }

    /**
     * 判断是否有未满足的条件
     */
    public boolean hasUnmetConditions() {
        if (conditionResults == null || conditionResults.isEmpty()) {
            return false;
        }
        return conditionResults.values().stream().anyMatch(Boolean.FALSE::equals);
    }

    /**
     * 按操作编码过滤权限条目
     */
    public List<GrantedPermission> filterByOperation(String operationCode) {
        return allPermissions().stream()
            .filter(p -> Objects.equals(operationCode, p.operationCode()))
            .toList();
    }

    /**
     * 检查是否有指定资源的权限（按位匹配）
     */
    public boolean hasPermissionForResource(Long resourceEntityId, long targetBit) {
        return allPermissions().stream()
            .filter(p -> Objects.equals(resourceEntityId, p.resourceEntityId()) || p.isScopeAll())
            .anyMatch(p -> p.matchesBit(targetBit));
    }

    /**
     * 获取指定资源的权限条目
     */
    public List<GrantedPermission> getPermissionsForResource(Long resourceEntityId) {
        return allPermissions().stream()
            .filter(p -> Objects.equals(resourceEntityId, p.resourceEntityId()) || p.isScopeAll())
            .toList();
    }

    /**
     * 获取可授权的权限条目
     */
    public List<GrantedPermission> getGrantablePermissions() {
        return allPermissions().stream()
            .filter(GrantedPermission::isGrantable)
            .toList();
    }

    /**
     * 获取有效的权限条目（条件满足 + 无冲突）
     */
    public List<GrantedPermission> getEffectivePermissions() {
        return allPermissions().stream()
            .filter(GrantedPermission::isEffective)
            .toList();
    }

    // ===== Builder =====

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建空结果
     */
    public static PermissionQueryResult empty() {
        return new PermissionQueryResult(
            List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), List.of(), null, Set.of(), Set.of()
        );
    }

    public static final class Builder {
        private List<GrantedPermission> scopeAllPermissions = List.of();
        private List<GrantedPermission> instancePermissions = List.of();
        private Map<Long, AbstractRole> roles = Map.of();
        private Map<Long, ResourceEntity> resources = Map.of();
        private Map<Long, OperationPermission> operations = Map.of();
        private Map<Long, PermissionCondition> conditions = Map.of();
        private Map<Long, Boolean> conditionResults = Map.of();
        private List<ConflictInfo> conflicts = List.of();
        private Long tenantId;
        private Set<Long> resolvedRoleIds = Set.of();
        private Set<Integer> resolvedResourceTypes = Set.of();

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder resolvedRoleIds(Set<Long> roleIds) {
            this.resolvedRoleIds = roleIds != null ? roleIds : Set.of();
            return this;
        }

        public Builder resolvedResourceTypes(Set<Integer> types) {
            this.resolvedResourceTypes = types != null ? types : Set.of();
            return this;
        }

        public Builder scopeAllPermissions(List<GrantedPermission> perms) {
            this.scopeAllPermissions = perms != null ? perms : List.of();
            return this;
        }

        public Builder instancePermissions(List<GrantedPermission> perms) {
            this.instancePermissions = perms != null ? perms : List.of();
            return this;
        }

        public Builder roles(Map<Long, AbstractRole> roles) {
            this.roles = roles != null ? roles : Map.of();
            return this;
        }

        public Builder resources(Map<Long, ResourceEntity> resources) {
            this.resources = resources != null ? resources : Map.of();
            return this;
        }

        public Builder operations(Map<Long, OperationPermission> operations) {
            this.operations = operations != null ? operations : Map.of();
            return this;
        }

        public Builder conditions(Map<Long, PermissionCondition> conditions) {
            this.conditions = conditions != null ? conditions : Map.of();
            return this;
        }

        public Builder conditionResults(Map<Long, Boolean> results) {
            this.conditionResults = results != null ? results : Map.of();
            return this;
        }

        public Builder conflicts(List<ConflictInfo> conflicts) {
            this.conflicts = conflicts != null ? conflicts : List.of();
            return this;
        }

        public PermissionQueryResult build() {
            return new PermissionQueryResult(
                List.copyOf(scopeAllPermissions),
                List.copyOf(instancePermissions),
                Map.copyOf(roles),
                Map.copyOf(resources),
                Map.copyOf(operations),
                Map.copyOf(conditions),
                Map.copyOf(conditionResults),
                List.copyOf(conflicts),
                tenantId,
                Set.copyOf(resolvedRoleIds),
                Set.copyOf(resolvedResourceTypes)
            );
        }
    }
}