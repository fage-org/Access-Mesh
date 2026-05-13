package cn.ac.fage.accessmesh.permission.service.context;

import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
public class PermissionQueryContext {

    // ===== 输入参数（final，无 setter）=====
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
        return request.domainCode() != null && !request.domainCode().isBlank();
    }
}