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
 * 权限查询上下文对象
 * <p>
 * 封装多阶段权限查询流程中的所有状态和累积数据。
 * 用于 PermissionViewServiceImpl.getUserPermissionsWithFilters() 方法。
 * </p>
 */
public class PermissionQueryContext {

    // ===== 输入参数 =====
    private final Long tenantId;
    private final Long userId;
    private final UserPermissionViewReq request;

    // ===== 准备阶段 =====
    private AbstractUser user;
    private int pageNum;
    private int pageSize;
    private int offset;
    private Long domainId;

    // ===== 角色数据 =====
    private Set<Long> roleIds;
    private Set<Long> filteredRoleIds;
    private Map<Long, AbstractRole> roleMap;

    // ===== 权限数据 =====
    private List<RoleResourcePermission> allPermissions;
    private List<RoleResourcePermission> filteredPermissions;
    private List<RoleResourcePermission> pagedPermissions;
    private Map<Long, List<RoleResourcePermission>> groupedByResource;

    // ===== 关联实体 =====
    private Map<Long, OperationPermission> operationMap;
    private Map<Long, ResourceEntity> resourceMap;
    private Map<Long, String> domainCodeMap;

    // ===== 类型映射 =====
    private Map<Integer, String> resourceTypeCodeMap;
    private Map<Integer, String> roleTypeCodeMap;
    private Integer apiTypeValue;

    // ===== 分页结果 =====
    private long totalCount;
    private boolean hasNext;

    /**
     * 构造权限查询上下文
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param request  用户权限视图查询请求
     */
    public PermissionQueryContext(Long tenantId, Long userId, UserPermissionViewReq request) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.request = request;
    }

    // ===== Getter 和 Setter =====

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取查询请求
     *
     * @return 用户权限视图查询请求
     */
    public UserPermissionViewReq getRequest() {
        return request;
    }

    /**
     * 获取用户实体
     *
     * @return 用户实体
     */
    public AbstractUser getUser() {
        return user;
    }

    /**
     * 设置用户实体
     *
     * @param user 用户实体
     */
    public void setUser(AbstractUser user) {
        this.user = user;
    }

    /**
     * 获取页码
     *
     * @return 页码
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * 设置页码
     *
     * @param pageNum 页码
     */
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * 获取每页大小
     *
     * @return 每页大小
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页大小
     *
     * @param pageSize 每页大小
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 获取偏移量
     *
     * @return 偏移量
     */
    public int getOffset() {
        return offset;
    }

    /**
     * 设置偏移量
     *
     * @param offset 偏移量
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * 获取业务域ID
     *
     * @return 业务域ID
     */
    public Long getDomainId() {
        return domainId;
    }

    /**
     * 设置业务域ID
     *
     * @param domainId 业务域ID
     */
    public void setDomainId(Long domainId) {
        this.domainId = domainId;
    }

    /**
     * 获取角色ID集合
     *
     * @return 角色ID集合
     */
    public Set<Long> getRoleIds() {
        return roleIds;
    }

    /**
     * 设置角色ID集合
     *
     * @param roleIds 角色ID集合
     */
    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds;
    }

    /**
     * 获取过滤后的角色ID集合
     *
     * @return 过滤后的角色ID集合
     */
    public Set<Long> getFilteredRoleIds() {
        return filteredRoleIds;
    }

    /**
     * 设置过滤后的角色ID集合
     *
     * @param filteredRoleIds 过滤后的角色ID集合
     */
    public void setFilteredRoleIds(Set<Long> filteredRoleIds) {
        this.filteredRoleIds = filteredRoleIds;
    }

    /**
     * 获取角色映射
     *
     * @return 角色ID到角色实体的映射
     */
    public Map<Long, AbstractRole> getRoleMap() {
        return roleMap;
    }

    /**
     * 设置角色映射
     *
     * @param roleMap 角色ID到角色实体的映射
     */
    public void setRoleMap(Map<Long, AbstractRole> roleMap) {
        this.roleMap = roleMap;
    }

    /**
     * 获取所有权限列表
     *
     * @return 所有权限列表
     */
    public List<RoleResourcePermission> getAllPermissions() {
        return allPermissions;
    }

    /**
     * 设置所有权限列表
     *
     * @param allPermissions 所有权限列表
     */
    public void setAllPermissions(List<RoleResourcePermission> allPermissions) {
        this.allPermissions = allPermissions;
    }

    /**
     * 获取过滤后的权限列表
     *
     * @return 过滤后的权限列表
     */
    public List<RoleResourcePermission> getFilteredPermissions() {
        return filteredPermissions;
    }

    /**
     * 设置过滤后的权限列表
     *
     * @param filteredPermissions 过滤后的权限列表
     */
    public void setFilteredPermissions(List<RoleResourcePermission> filteredPermissions) {
        this.filteredPermissions = filteredPermissions;
    }

    /**
     * 获取分页后的权限列表
     *
     * @return 分页后的权限列表
     */
    public List<RoleResourcePermission> getPagedPermissions() {
        return pagedPermissions;
    }

    /**
     * 设置分页后的权限列表
     *
     * @param pagedPermissions 分页后的权限列表
     */
    public void setPagedPermissions(List<RoleResourcePermission> pagedPermissions) {
        this.pagedPermissions = pagedPermissions;
    }

    /**
     * 获取按资源分组的权限映射
     *
     * @return 资源ID到权限列表的映射
     */
    public Map<Long, List<RoleResourcePermission>> getGroupedByResource() {
        return groupedByResource;
    }

    /**
     * 设置按资源分组的权限映射
     *
     * @param groupedByResource 资源ID到权限列表的映射
     */
    public void setGroupedByResource(Map<Long, List<RoleResourcePermission>> groupedByResource) {
        this.groupedByResource = groupedByResource;
    }

    /**
     * 获取操作权限映射
     *
     * @return 操作权限ID到实体的映射
     */
    public Map<Long, OperationPermission> getOperationMap() {
        return operationMap;
    }

    /**
     * 设置操作权限映射
     *
     * @param operationMap 操作权限ID到实体的映射
     */
    public void setOperationMap(Map<Long, OperationPermission> operationMap) {
        this.operationMap = operationMap;
    }

    /**
     * 获取资源实体映射
     *
     * @return 资源ID到实体的映射
     */
    public Map<Long, ResourceEntity> getResourceMap() {
        return resourceMap;
    }

    /**
     * 设置资源实体映射
     *
     * @param resourceMap 资源ID到实体的映射
     */
    public void setResourceMap(Map<Long, ResourceEntity> resourceMap) {
        this.resourceMap = resourceMap;
    }

    /**
     * 获取业务域编码映射
     *
     * @return 业务域ID到编码的映射
     */
    public Map<Long, String> getDomainCodeMap() {
        return domainCodeMap;
    }

    /**
     * 设置业务域编码映射
     *
     * @param domainCodeMap 业务域ID到编码的映射
     */
    public void setDomainCodeMap(Map<Long, String> domainCodeMap) {
        this.domainCodeMap = domainCodeMap;
    }

    /**
     * 获取资源类型编码映射
     *
     * @return 资源类型值到编码的映射
     */
    public Map<Integer, String> getResourceTypeCodeMap() {
        return resourceTypeCodeMap;
    }

    /**
     * 设置资源类型编码映射
     *
     * @param resourceTypeCodeMap 资源类型值到编码的映射
     */
    public void setResourceTypeCodeMap(Map<Integer, String> resourceTypeCodeMap) {
        this.resourceTypeCodeMap = resourceTypeCodeMap;
    }

    /**
     * 获取角色类型编码映射
     *
     * @return 角色类型值到编码的映射
     */
    public Map<Integer, String> getRoleTypeCodeMap() {
        return roleTypeCodeMap;
    }

    /**
     * 设置角色类型编码映射
     *
     * @param roleTypeCodeMap 角色类型值到编码的映射
     */
    public void setRoleTypeCodeMap(Map<Integer, String> roleTypeCodeMap) {
        this.roleTypeCodeMap = roleTypeCodeMap;
    }

    /**
     * 获取API类型值
     *
     * @return API资源类型值
     */
    public Integer getApiTypeValue() {
        return apiTypeValue;
    }

    /**
     * 设置API类型值
     *
     * @param apiTypeValue API资源类型值
     */
    public void setApiTypeValue(Integer apiTypeValue) {
        this.apiTypeValue = apiTypeValue;
    }

    /**
     * 获取总数
     *
     * @return 总记录数
     */
    public long getTotalCount() {
        return totalCount;
    }

    /**
     * 设置总数
     *
     * @param totalCount 总记录数
     */
    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * 是否有下一页
     *
     * @return 是否有下一页
     */
    public boolean isHasNext() {
        return hasNext;
    }

    /**
     * 设置是否有下一页
     *
     * @param hasNext 是否有下一页
     */
    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    // ===== 便捷方法 =====

    /**
     * 检查用户是否未找到
     *
     * @return 用户未找到返回true
     */
    public boolean isUserNotFound() {
        return user == null;
    }

    /**
     * 检查用户是否有角色
     *
     * @return 无角色返回true
     */
    public boolean hasNoRoles() {
        return roleIds == null || roleIds.isEmpty();
    }

    /**
     * 检查过滤后是否无角色
     *
     * @return 过滤后无角色返回true
     */
    public boolean hasNoFilteredRoles() {
        return filteredRoleIds == null || filteredRoleIds.isEmpty();
    }

    /**
     * 检查是否无权限
     *
     * @return 无权限返回true
     */
    public boolean hasNoPermissions() {
        return allPermissions == null || allPermissions.isEmpty();
    }

    /**
     * 是否应在结果中包含范围信息
     *
     * @return 应包含返回true
     */
    public boolean shouldIncludeScopes() {
        return !Boolean.FALSE.equals(request.includeScopes());
    }

    /**
     * 是否应包含API资源
     *
     * @return 应包含返回true
     */
    public boolean shouldIncludeApiResources() {
        return !Boolean.FALSE.equals(request.includeApiResources());
    }

    /**
     * 是否应包含来源角色
     *
     * @return 应包含返回true
     */
    public boolean shouldIncludeSourceRoles() {
        return request.includeSourceRoles() == null || request.includeSourceRoles();
    }

    /**
     * 获取来源角色数量限制
     *
     * @return 来源角色数量限制
     */
    public int getSourceRoleLimit() {
        return request.sourceRoleLimit() == null ? 20 : Math.max(request.sourceRoleLimit(), 0);
    }

    /**
     * 获取操作码过滤器
     *
     * @return 操作码列表
     */
    public List<String> getOperationCodesFilter() {
        return request.operationCodes();
    }

    /**
     * 是否有操作码过滤器
     *
     * @return 有过滤器返回true
     */
    public boolean hasOperationCodesFilter() {
        return request.operationCodes() != null && !request.operationCodes().isEmpty();
    }

    /**
     * 是否有资源类型码过滤器
     *
     * @return 有过滤器返回true
     */
    public boolean hasResourceTypeCodesFilter() {
        return request.resourceTypeCodes() != null && !request.resourceTypeCodes().isEmpty();
    }

    /**
     * 是否有资源关键字过滤器
     *
     * @return 有过滤器返回true
     */
    public boolean hasResourceKeywordFilter() {
        return request.resourceKeyword() != null && !request.resourceKeyword().isBlank();
    }

    /**
     * 是否有业务域过滤器
     *
     * @return 有过滤器返回true
     */
    public boolean hasDomainFilter() {
        return request.domainCode() != null;
    }
}