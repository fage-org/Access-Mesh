package cn.ac.fage.accessmesh.permission.dto.query;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限视图查询结果
 * <p>
 * 承载用户视图的权限数据，独立于 {@link PermResult}。
 * 包含权限条目列表及关联的资源、操作、角色映射。
 * 通过 {@link Builder} 模式构造，确保不可变。
 * </p>
 */
public class PermViewResult {

    /**
     * 权限条目列表（经过过滤和分页）
     */
    private final List<RolePermEntry> entries;

    /**
     * 资源实体映射（key: resourceEntityId）
     */
    private final Map<Long, ResourceEntity> resourceMap;

    /**
     * 操作权限映射（key: operationPermissionId）
     */
    private final Map<Long, OperationPermission> operationMap;

    /**
     * 角色映射（key: roleId）
     */
    private final Map<Long, AbstractRole> roleMap;

    /**
     * 总记录数（分页前）
     */
    private final long totalCount;

    /**
     * 当前页码
     */
    private final int pageNum;

    /**
     * 每页条数
     */
    private final int pageSize;

    /**
     * 是否有下一页
     */
    private final boolean hasNext;

    /**
     * 来源角色信息映射（key: roleId）
     * <p>
     * 仅在 includeSourceRoles=true 时填充。
     * </p>
     */
    private final Map<Long, RoleInfo> sourceRoleMap;

    /**
     * 资源类型码映射（key: resourceId → resourceTypeCode）
     */
    private final Map<Long, String> resourceTypeCodeMap;

    /**
     * 域编码映射（key: resourceId → domainCode）
     * <p>
     * 通过 DomainClassifyService 按资源类型码反查域编码。
     * </p>
     */
    private final Map<Long, String> domainCodeMap;

    /**
     * 来源角色信息
     */
    public record RoleInfo(String typeCode, String externalId, String name) {}

    /**
     * 构造权限视图结果
     *
     * @param b Builder 实例
     */
    private PermViewResult(Builder b) {
        this.entries = List.copyOf(b.entries);
        this.resourceMap = b.resourceMap == null ? Map.of() : Map.copyOf(b.resourceMap);
        this.operationMap = b.operationMap == null ? Map.of() : Map.copyOf(b.operationMap);
        this.roleMap = b.roleMap == null ? Map.of() : Map.copyOf(b.roleMap);
        this.totalCount = b.totalCount;
        this.pageNum = b.pageNum;
        this.pageSize = b.pageSize;
        this.hasNext = b.hasNext;
        this.sourceRoleMap = b.sourceRoleMap == null ? Map.of() : Map.copyOf(b.sourceRoleMap);
        this.resourceTypeCodeMap = b.resourceTypeCodeMap == null ? Map.of() : Map.copyOf(b.resourceTypeCodeMap);
        this.domainCodeMap = b.domainCodeMap == null ? Map.of() : Map.copyOf(b.domainCodeMap);
    }

    // ===== 工厂方法 =====

    /**
     * 创建 Builder 实例
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ===== Getter 方法 =====

    /**
     * 获取权限条目列表
     *
     * @return 权限条目列表
     */
    public List<RolePermEntry> getEntries() {
        return entries;
    }

    /**
     * 获取资源实体映射
     *
     * @return 资源实体映射
     */
    public Map<Long, ResourceEntity> getResourceMap() {
        return resourceMap;
    }

    /**
     * 获取操作权限映射
     *
     * @return 操作权限映射
     */
    public Map<Long, OperationPermission> getOperationMap() {
        return operationMap;
    }

    /**
     * 获取角色映射
     *
     * @return 角色映射
     */
    public Map<Long, AbstractRole> getRoleMap() {
        return roleMap;
    }

    /**
     * 获取总记录数
     *
     * @return 总记录数
     */
    public long getTotalCount() {
        return totalCount;
    }

    /**
     * 获取当前页码
     *
     * @return 当前页码
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * 获取每页条数
     *
     * @return 每页条数
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 获取是否有下一页
     *
     * @return 是否有下一页
     */
    public boolean isHasNext() {
        return hasNext;
    }

    public Map<Long, RoleInfo> getSourceRoleMap() {
        return sourceRoleMap;
    }

    public Map<Long, String> getResourceTypeCodeMap() {
        return resourceTypeCodeMap;
    }

    public Map<Long, String> getDomainCodeMap() {
        return domainCodeMap;
    }

    // ===== Builder 类 =====

    /**
     * 权限视图结果 Builder
     * <p>
     * 用于构建 {@link PermViewResult} 实例，支持链式调用。
     * </p>
     */
    public static final class Builder {
        private List<RolePermEntry> entries = List.of();
        private Map<Long, ResourceEntity> resourceMap;
        private Map<Long, OperationPermission> operationMap;
        private Map<Long, AbstractRole> roleMap;
        private long totalCount;
        private int pageNum;
        private int pageSize;
        private boolean hasNext;
        private Map<Long, RoleInfo> sourceRoleMap;
        private Map<Long, String> resourceTypeCodeMap;
        private Map<Long, String> domainCodeMap;

        private Builder() {
        }

        /**
         * 设置权限条目列表
         *
         * @param v 权限条目列表
         * @return Builder 实例
         */
        public Builder entries(List<RolePermEntry> v) {
            this.entries = v != null ? v : List.of();
            return this;
        }

        /**
         * 设置资源实体映射
         *
         * @param v 资源实体映射
         * @return Builder 实例
         */
        public Builder resourceMap(Map<Long, ResourceEntity> v) {
            this.resourceMap = v;
            return this;
        }

        /**
         * 设置操作权限映射
         *
         * @param v 操作权限映射
         * @return Builder 实例
         */
        public Builder operationMap(Map<Long, OperationPermission> v) {
            this.operationMap = v;
            return this;
        }

        /**
         * 设置角色映射
         *
         * @param v 角色映射
         * @return Builder 实例
         */
        public Builder roleMap(Map<Long, AbstractRole> v) {
            this.roleMap = v;
            return this;
        }

        /**
         * 设置总记录数
         *
         * @param v 总记录数
         * @return Builder 实例
         */
        public Builder totalCount(long v) {
            this.totalCount = v;
            return this;
        }

        /**
         * 设置当前页码
         *
         * @param v 当前页码
         * @return Builder 实例
         */
        public Builder pageNum(int v) {
            this.pageNum = v;
            return this;
        }

        /**
         * 设置每页条数
         *
         * @param v 每页条数
         * @return Builder 实例
         */
        public Builder pageSize(int v) {
            this.pageSize = v;
            return this;
        }

        /**
         * 设置是否有下一页
         *
         * @param v 是否有下一页
         * @return Builder 实例
         */
        public Builder hasNext(boolean v) {
            this.hasNext = v;
            return this;
        }

        public Builder sourceRoleMap(Map<Long, RoleInfo> v) {
            this.sourceRoleMap = v;
            return this;
        }

        public Builder resourceTypeCodeMap(Map<Long, String> v) {
            this.resourceTypeCodeMap = v;
            return this;
        }

        public Builder domainCodeMap(Map<Long, String> v) {
            this.domainCodeMap = v;
            return this;
        }

        /**
         * 构建 {@link PermViewResult} 实例
         *
         * @return PermViewResult 实例
         */
        public PermViewResult build() {
            return new PermViewResult(this);
        }
    }
}
