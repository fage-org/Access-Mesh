package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.*;
import java.util.stream.Stream;

/**
 * 统一权限查询结果类
 * <p>
 * 包含权限引擎收集的所有信息，调用者通过便捷方法获取所需内容。
 * 空字段表示"未请求"，根据查询选项决定。
 * </p>
 */
public class PermResult {

    /**
     * 是否允许
     */
    private final boolean allowed;

    /**
     * 原因说明（拒绝时填写）
     */
    private final String reason;

    /**
     * 全范围是否匹配
     */
    private final boolean scopeAllMatched;

    /**
     * 全范围权限条目列表
     */
    private final List<RolePermEntry> scopeAllEntries;

    /**
     * 实例级权限条目列表
     */
    private final List<RolePermEntry> instanceEntries;

    /**
     * 资源实体映射
     */
    private final Map<Long, ResourceEntity> resourceMap;

    /**
     * 操作权限映射
     */
    private final Map<Long, OperationPermission> operationMap;

    /**
     * 角色映射
     */
    private final Map<Long, AbstractRole> roleMap;

    /**
     * 构造权限结果
     * <p>
     * 通过Builder构造，确保不可变。
     * </p>
     *
     * @param b Builder实例
     */
    private PermResult(Builder b) {
        this.allowed = b.allowed;
        this.reason = b.reason;
        this.scopeAllMatched = b.scopeAllMatched;
        this.scopeAllEntries = List.copyOf(b.scopeAllEntries);
        this.instanceEntries = List.copyOf(b.instanceEntries);
        this.resourceMap = b.resourceMap == null ? null : Map.copyOf(b.resourceMap);
        this.operationMap = b.operationMap == null ? null : Map.copyOf(b.operationMap);
        this.roleMap = b.roleMap == null ? null : Map.copyOf(b.roleMap);
    }

    // ===== 工厂方法 =====

    /**
     * 创建Builder实例
     *
     * @param allowed 是否允许
     * @param reason  原因说明
     * @return Builder实例
     */
    public static Builder builder(boolean allowed, String reason) {
        return new Builder(allowed, reason);
    }

    /**
     * 创建拒绝结果
     *
     * @param reason 拒绝原因
     * @return 拒绝结果实例
     */
    public static PermResult deny(String reason) {
        return builder(false, reason).build();
    }

    
    // ===== Getter方法 =====

    /**
     * 获取是否允许
     *
     * @return 是否允许
     */
    public boolean allowed() { return allowed; }

    /**
     * 获取原因说明
     *
     * @return 原因说明
     */
    public String reason() { return reason; }

    /**
     * 获取全范围是否匹配
     *
     * @return 全范围是否匹配
     */
    public boolean scopeAllMatched() { return scopeAllMatched; }

    
    /**
     * 获取实例级权限条目列表
     *
     * @return 实例级权限条目列表
     */
    public List<RolePermEntry> instanceEntries() { return instanceEntries; }

    /**
     * 获取资源实体映射
     *
     * @return 资源实体映射
     */
    public Map<Long, ResourceEntity> resourceMap() { return resourceMap; }

    /**
     * 获取操作权限映射
     *
     * @return 操作权限映射
     */
    public Map<Long, OperationPermission> operationMap() { return operationMap; }

    /**
     * 获取角色映射
     *
     * @return 角色映射
     */
    public Map<Long, AbstractRole> roleMap() { return roleMap; }

    
    // ===== 便捷方法 =====

    /**
     * 获取所有权限条目（合并全范围和实例级）
     * <p>
     * 合并scopeAllEntries和instanceEntries为单一列表。
     * </p>
     *
     * @return 所有权限条目列表
     */
    public List<RolePermEntry> allEntries() {
        return Stream.concat(scopeAllEntries.stream(), instanceEntries.stream()).toList();
    }

    /**
     * 获取匹配的角色ID集合
     * <p>
     * 从所有权限条目中提取不重复的角色ID。
     * </p>
     *
     * @return 匹配的角色ID集合
     */
    public Set<Long> matchedRoleIds() {
        return allEntries().stream()
            .map(RolePermEntry::roleId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取匹配的权限ID集合
     * <p>
     * 从所有权限条目中提取不重复的权限ID。
     * </p>
     *
     * @return 匹配的权限ID集合
     */
    public Set<Long> matchedPermissionIds() {
        return allEntries().stream()
            .map(RolePermEntry::permissionId).filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    }

    
    // ===== Builder类 =====

    /**
     * 权限结果Builder类
     * <p>
     * 用于构建PermResult实例，支持链式调用。
     * </p>
     */
    public static final class Builder {
        private final boolean allowed;
        private final String reason;
        private boolean scopeAllMatched;
        private List<RolePermEntry> scopeAllEntries = List.of();
        private List<RolePermEntry> instanceEntries = List.of();
        private Map<Long, ResourceEntity> resourceMap;
        private Map<Long, OperationPermission> operationMap;
        private Map<Long, AbstractRole> roleMap;

        /**
         * 构造Builder
         *
         * @param allowed 是否允许
         * @param reason  原因说明
         */
        private Builder(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        /**
         * 设置全范围是否匹配
         *
         * @param v 是否匹配
         * @return Builder实例
         */
        public Builder scopeAllMatched(boolean v) { this.scopeAllMatched = v; return this; }

        /**
         * 设置全范围权限条目列表
         *
         * @param v 权限条目列表
         * @return Builder实例
         */
        public Builder scopeAllEntries(List<RolePermEntry> v) { this.scopeAllEntries = v != null ? v : List.of(); return this; }

        /**
         * 设置实例级权限条目列表
         *
         * @param v 权限条目列表
         * @return Builder实例
         */
        public Builder instanceEntries(List<RolePermEntry> v) { this.instanceEntries = v != null ? v : List.of(); return this; }

        /**
         * 设置资源实体映射
         *
         * @param v 资源实体映射
         * @return Builder实例
         */
        public Builder resourceMap(Map<Long, ResourceEntity> v) { this.resourceMap = v; return this; }

        /**
         * 设置操作权限映射
         *
         * @param v 操作权限映射
         * @return Builder实例
         */
        public Builder operationMap(Map<Long, OperationPermission> v) { this.operationMap = v; return this; }

        /**
         * 设置角色映射
         *
         * @param v 角色映射
         * @return Builder实例
         */
        public Builder roleMap(Map<Long, AbstractRole> v) { this.roleMap = v; return this; }

        
        /**
         * 构建PermResult实例
         *
         * @return PermResult实例
         */
        public PermResult build() { return new PermResult(this); }
    }
}