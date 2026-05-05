package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.*;
import java.util.stream.Stream;

/**
 * Unified permission query result.
 *
 * <p>Contains all information the engine gathered; callers access what they need
 * via convenience methods.  Null fields indicate "not requested" per the query options.
 */
public class PermResult {

    private final boolean allowed;
    private final String reason;
    private final boolean scopeAllMatched;

    private final List<RolePermEntry> scopeAllEntries;
    private final List<RolePermEntry> instanceEntries;

    private final Map<Long, ResourceEntity> resourceMap;
    private final Map<Long, OperationPermission> operationMap;
    private final Map<Long, AbstractRole> roleMap;
    private final Map<Long, BizDomain> domainMap;
    private final Map<Long, PermissionCondition> conditionMap;

    private PermResult(Builder b) {
        this.allowed = b.allowed;
        this.reason = b.reason;
        this.scopeAllMatched = b.scopeAllMatched;
        this.scopeAllEntries = List.copyOf(b.scopeAllEntries);
        this.instanceEntries = List.copyOf(b.instanceEntries);
        this.resourceMap = b.resourceMap == null ? null : Map.copyOf(b.resourceMap);
        this.operationMap = b.operationMap == null ? null : Map.copyOf(b.operationMap);
        this.roleMap = b.roleMap == null ? null : Map.copyOf(b.roleMap);
        this.domainMap = b.domainMap == null ? null : Map.copyOf(b.domainMap);
        this.conditionMap = b.conditionMap == null ? null : Map.copyOf(b.conditionMap);
    }

    // ===== factories =====

    public static Builder builder(boolean allowed, String reason) {
        return new Builder(allowed, reason);
    }

    public static PermResult deny(String reason) {
        return builder(false, reason).build();
    }

    public static PermResult allow(boolean scopeAllMatched) {
        return builder(true, null).scopeAllMatched(scopeAllMatched).build();
    }

    // ===== getters =====

    public boolean allowed() { return allowed; }
    public String reason() { return reason; }
    public boolean scopeAllMatched() { return scopeAllMatched; }
    public List<RolePermEntry> scopeAllEntries() { return scopeAllEntries; }
    public List<RolePermEntry> instanceEntries() { return instanceEntries; }
    public Map<Long, ResourceEntity> resourceMap() { return resourceMap; }
    public Map<Long, OperationPermission> operationMap() { return operationMap; }
    public Map<Long, AbstractRole> roleMap() { return roleMap; }
    public Map<Long, BizDomain> domainMap() { return domainMap; }
    public Map<Long, PermissionCondition> conditionMap() { return conditionMap; }

    // ===== convenience =====

    /** All entries (scopeAll + instance) merged. */
    public List<RolePermEntry> allEntries() {
        return Stream.concat(scopeAllEntries.stream(), instanceEntries.stream()).toList();
    }

    public Set<Long> matchedRoleIds() {
        return allEntries().stream()
            .map(RolePermEntry::roleId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    public Set<Long> matchedPermissionIds() {
        return allEntries().stream()
            .map(RolePermEntry::permissionId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    /** Whether any matched entry has canGrant=true. */
    public boolean hasCanGrant() {
        return allEntries().stream().anyMatch(e -> Boolean.TRUE.equals(e.canGrant()));
    }

    // ===== builder =====

    public static final class Builder {
        private final boolean allowed;
        private final String reason;
        private boolean scopeAllMatched;
        private List<RolePermEntry> scopeAllEntries = List.of();
        private List<RolePermEntry> instanceEntries = List.of();
        private Map<Long, ResourceEntity> resourceMap;
        private Map<Long, OperationPermission> operationMap;
        private Map<Long, AbstractRole> roleMap;
        private Map<Long, BizDomain> domainMap;
        private Map<Long, PermissionCondition> conditionMap;

        private Builder(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public Builder scopeAllMatched(boolean v) { this.scopeAllMatched = v; return this; }
        public Builder scopeAllEntries(List<RolePermEntry> v) { this.scopeAllEntries = v != null ? v : List.of(); return this; }
        public Builder instanceEntries(List<RolePermEntry> v) { this.instanceEntries = v != null ? v : List.of(); return this; }
        public Builder resourceMap(Map<Long, ResourceEntity> v) { this.resourceMap = v; return this; }
        public Builder operationMap(Map<Long, OperationPermission> v) { this.operationMap = v; return this; }
        public Builder roleMap(Map<Long, AbstractRole> v) { this.roleMap = v; return this; }
        public Builder domainMap(Map<Long, BizDomain> v) { this.domainMap = v; return this; }
        public Builder conditionMap(Map<Long, PermissionCondition> v) { this.conditionMap = v; return this; }

        public PermResult build() { return new PermResult(this); }
    }
}
