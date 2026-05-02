package cn.ac.fage.accessmesh.permission.service.context;

import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context object for permission query processing.
 * Encapsulates all state and data accumulated during the multi-stage permission query workflow.
 * Used by PermissionViewServiceImpl.getUserPermissionsWithFilters().
 */
public class PermissionQueryContext {

    // ===== Input parameters =====
    private final Long tenantId;
    private final Long userId;
    private final UserPermissionViewReq request;

    // ===== Preparation stage =====
    private AbstractUser user;
    private int pageNum;
    private int pageSize;
    private int offset;
    private Long domainId;

    // ===== Role data =====
    private Set<Long> roleIds;
    private Set<Long> filteredRoleIds;
    private Map<Long, AbstractRole> roleMap;

    // ===== Permission data =====
    private List<RoleResourcePermission> allPermissions;
    private List<RoleResourcePermission> filteredPermissions;
    private List<RoleResourcePermission> pagedPermissions;
    private Map<Long, List<RoleResourcePermission>> groupedByResource;

    // ===== Related entities =====
    private Map<Long, OperationPermission> operationMap;
    private Map<Long, ResourceEntity> resourceMap;
    private Map<Long, String> domainCodeMap;

    // ===== Type mappings =====
    private Map<Integer, String> resourceTypeCodeMap;
    private Map<Integer, String> roleTypeCodeMap;
    private Integer apiTypeValue;

    // ===== Pagination result =====
    private long totalCount;
    private boolean hasNext;

    public PermissionQueryContext(Long tenantId, Long userId, UserPermissionViewReq request) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.request = request;
    }

    // ===== Getters and Setters =====

    public Long getTenantId() {
        return tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public UserPermissionViewReq getRequest() {
        return request;
    }

    public AbstractUser getUser() {
        return user;
    }

    public void setUser(AbstractUser user) {
        this.user = user;
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

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public Long getDomainId() {
        return domainId;
    }

    public void setDomainId(Long domainId) {
        this.domainId = domainId;
    }

    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public Set<Long> getFilteredRoleIds() {
        return filteredRoleIds;
    }

    public void setFilteredRoleIds(Set<Long> filteredRoleIds) {
        this.filteredRoleIds = filteredRoleIds;
    }

    public Map<Long, AbstractRole> getRoleMap() {
        return roleMap;
    }

    public void setRoleMap(Map<Long, AbstractRole> roleMap) {
        this.roleMap = roleMap;
    }

    public List<RoleResourcePermission> getAllPermissions() {
        return allPermissions;
    }

    public void setAllPermissions(List<RoleResourcePermission> allPermissions) {
        this.allPermissions = allPermissions;
    }

    public List<RoleResourcePermission> getFilteredPermissions() {
        return filteredPermissions;
    }

    public void setFilteredPermissions(List<RoleResourcePermission> filteredPermissions) {
        this.filteredPermissions = filteredPermissions;
    }

    public List<RoleResourcePermission> getPagedPermissions() {
        return pagedPermissions;
    }

    public void setPagedPermissions(List<RoleResourcePermission> pagedPermissions) {
        this.pagedPermissions = pagedPermissions;
    }

    public Map<Long, List<RoleResourcePermission>> getGroupedByResource() {
        return groupedByResource;
    }

    public void setGroupedByResource(Map<Long, List<RoleResourcePermission>> groupedByResource) {
        this.groupedByResource = groupedByResource;
    }

    public Map<Long, OperationPermission> getOperationMap() {
        return operationMap;
    }

    public void setOperationMap(Map<Long, OperationPermission> operationMap) {
        this.operationMap = operationMap;
    }

    public Map<Long, ResourceEntity> getResourceMap() {
        return resourceMap;
    }

    public void setResourceMap(Map<Long, ResourceEntity> resourceMap) {
        this.resourceMap = resourceMap;
    }

    public Map<Long, String> getDomainCodeMap() {
        return domainCodeMap;
    }

    public void setDomainCodeMap(Map<Long, String> domainCodeMap) {
        this.domainCodeMap = domainCodeMap;
    }

    public Map<Integer, String> getResourceTypeCodeMap() {
        return resourceTypeCodeMap;
    }

    public void setResourceTypeCodeMap(Map<Integer, String> resourceTypeCodeMap) {
        this.resourceTypeCodeMap = resourceTypeCodeMap;
    }

    public Map<Integer, String> getRoleTypeCodeMap() {
        return roleTypeCodeMap;
    }

    public void setRoleTypeCodeMap(Map<Integer, String> roleTypeCodeMap) {
        this.roleTypeCodeMap = roleTypeCodeMap;
    }

    public Integer getApiTypeValue() {
        return apiTypeValue;
    }

    public void setApiTypeValue(Integer apiTypeValue) {
        this.apiTypeValue = apiTypeValue;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    // ===== Convenience methods =====

    /**
     * Check if user was not found.
     */
    public boolean isUserNotFound() {
        return user == null;
    }

    /**
     * Check if user has no roles.
     */
    public boolean hasNoRoles() {
        return roleIds == null || roleIds.isEmpty();
    }

    /**
     * Check if no roles remain after filtering.
     */
    public boolean hasNoFilteredRoles() {
        return filteredRoleIds == null || filteredRoleIds.isEmpty();
    }

    /**
     * Check if no permissions found.
     */
    public boolean hasNoPermissions() {
        return allPermissions == null || allPermissions.isEmpty();
    }

    /**
     * Check if scopes should be included in results.
     */
    public boolean shouldIncludeScopes() {
        return !Boolean.FALSE.equals(request.includeScopes());
    }

    /**
     * Check if API resources should be included.
     */
    public boolean shouldIncludeApiResources() {
        return !Boolean.FALSE.equals(request.includeApiResources());
    }

    /**
     * Check if source roles should be included.
     */
    public boolean shouldIncludeSourceRoles() {
        return request.includeSourceRoles() == null || request.includeSourceRoles();
    }

    /**
     * Get the limit for source roles to return.
     */
    public int getSourceRoleLimit() {
        return request.sourceRoleLimit() == null ? 20 : Math.max(request.sourceRoleLimit(), 0);
    }

    /**
     * Get operation codes filter from request.
     */
    public List<String> getOperationCodesFilter() {
        return request.operationCodes();
    }

    /**
     * Check if operation codes filter is specified.
     */
    public boolean hasOperationCodesFilter() {
        return request.operationCodes() != null && !request.operationCodes().isEmpty();
    }

    /**
     * Check if resource type codes filter is specified.
     */
    public boolean hasResourceTypeCodesFilter() {
        return request.resourceTypeCodes() != null && !request.resourceTypeCodes().isEmpty();
    }

    /**
     * Check if resource keyword filter is specified.
     */
    public boolean hasResourceKeywordFilter() {
        return request.resourceKeyword() != null && !request.resourceKeyword().isBlank();
    }

    /**
     * Check if domain filter is specified.
     */
    public boolean hasDomainFilter() {
        return request.domainCode() != null;
    }
}