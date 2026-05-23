package cn.ac.fage.accessmesh.permission.dto.query;

import java.util.Set;

/**
 * 权限视图过滤条件
 * <p>
 * 包含视图展示层的过滤和分页参数。
 * 引擎不消费此类，仅 {@code AppService} 层使用。
 * 具体过滤/分页逻辑将在 Phase 4 由 {@code PermViewAssembler} 实现。
 * </p>
 */
public class PermViewFilter {

    /**
     * 按资源类型编码过滤（可选）
     */
    private Set<String> resourceTypes;

    /**
     * 按操作编码过滤（可选）
     */
    private Set<String> operationCodes;

    /**
     * 按资源关键字搜索（可选）
     */
    private String resourceKeyword;

    /**
     * 按业务域编码过滤（可选）
     */
    private String domainCode;

    /**
     * 是否排除 API 类型资源
     */
    private boolean excludeApiResources;

    /**
     * 是否包含全范围权限条目（scopeAll）
     */
    private boolean includeScopePermissions;

    /**
     * 是否包含来源角色信息
     */
    private boolean includeSourceRoles;

    /**
     * 来源角色数量上限
     */
    private int sourceRoleLimit;

    /**
     * 页码，从 1 开始
     */
    private int pageNum;

    /**
     * 每页条数
     */
    private int pageSize;

    /**
     * 无参构造
     */
    public PermViewFilter() {
    }

    /**
     * 全参构造
     *
     * @param resourceTypes          按资源类型编码过滤
     * @param operationCodes         按操作编码过滤
     * @param resourceKeyword        按资源关键字搜索
     * @param domainCode             按业务域编码过滤
     * @param excludeApiResources    是否排除 API 类型资源
     * @param includeScopePermissions 是否包含全范围权限条目
     * @param includeSourceRoles     是否包含来源角色信息
     * @param sourceRoleLimit        来源角色数量上限
     * @param pageNum                页码
     * @param pageSize               每页条数
     */
    public PermViewFilter(Set<String> resourceTypes, Set<String> operationCodes,
                          String resourceKeyword, String domainCode,
                          boolean excludeApiResources, boolean includeScopePermissions,
                          boolean includeSourceRoles, int sourceRoleLimit,
                          int pageNum, int pageSize) {
        this.resourceTypes = resourceTypes;
        this.operationCodes = operationCodes;
        this.resourceKeyword = resourceKeyword;
        this.domainCode = domainCode;
        this.excludeApiResources = excludeApiResources;
        this.includeScopePermissions = includeScopePermissions;
        this.includeSourceRoles = includeSourceRoles;
        this.sourceRoleLimit = sourceRoleLimit;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    // ===== Getter/Setter =====

    public Set<String> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(Set<String> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    public Set<String> getOperationCodes() {
        return operationCodes;
    }

    public void setOperationCodes(Set<String> operationCodes) {
        this.operationCodes = operationCodes;
    }

    public String getResourceKeyword() {
        return resourceKeyword;
    }

    public void setResourceKeyword(String resourceKeyword) {
        this.resourceKeyword = resourceKeyword;
    }

    public String getDomainCode() {
        return domainCode;
    }

    public void setDomainCode(String domainCode) {
        this.domainCode = domainCode;
    }

    public boolean isExcludeApiResources() {
        return excludeApiResources;
    }

    public void setExcludeApiResources(boolean excludeApiResources) {
        this.excludeApiResources = excludeApiResources;
    }

    public boolean isIncludeScopePermissions() {
        return includeScopePermissions;
    }

    public void setIncludeScopePermissions(boolean includeScopePermissions) {
        this.includeScopePermissions = includeScopePermissions;
    }

    public boolean isIncludeSourceRoles() {
        return includeSourceRoles;
    }

    public void setIncludeSourceRoles(boolean includeSourceRoles) {
        this.includeSourceRoles = includeSourceRoles;
    }

    public int getSourceRoleLimit() {
        return sourceRoleLimit;
    }

    public void setSourceRoleLimit(int sourceRoleLimit) {
        this.sourceRoleLimit = sourceRoleLimit;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
