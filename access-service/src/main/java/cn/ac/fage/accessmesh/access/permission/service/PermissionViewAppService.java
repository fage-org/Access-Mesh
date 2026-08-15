package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserEffectiveRolesReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.EffectiveRoleResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionViewResp;

import java.util.List;
import java.util.Set;

/**
 * 权限视图应用服务接口
 * <p>
 * 提供只读的权限可见性API，用于查看用户、资源、角色的权限信息。
 * 支持权限解释、有效角色列表等功能。
 * recentChanges 已迁移至 LogQueryAppService，不在此接口中。
 * </p>
 */
public interface PermissionViewAppService {

    /**
     * 获取用户/角色的有效权限（带筛选和分页）
     *
     * @param tenantId 租户ID
     * @param req      用户权限视图请求
     * @return 有效权限响应
     */
    PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req);

    /**
     * 获取资源的权限信息
     *
     * @param tenantId         租户ID
     * @param domainCode       业务域编码
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型
     * @return 资源权限视图响应
     */
    ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType);

    /**
     * 获取角色的权限信息
     *
     * @param tenantId       租户ID
     * @param domainCode     业务域编码
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部ID
     * @param expandSub      是否展开子权限
     * @return 角色权限视图响应
     */
    RolePermissionViewResp getRolePermissions(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, boolean expandSub);

    /**
     * 解释权限判定结果
     *
     * @param tenantId 租户ID
     * @param req      权限解释请求
     * @return 权限解释响应
     */
    PermissionExplainResp explain(Long tenantId, PermissionExplainReq req);

    /**
     * 获取用户的有效角色列表
     *
     * @param tenantId 租户ID
     * @param req      有效角色查询请求
     * @return 有效角色列表响应
     */
    ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req);

    /**
     * 获取用户的资源权限树
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param req      用户资源树查询请求
     * @return 资源权限树响应列表
     */
    List<ResourcePermissionTreeResp> getUserResourceTree(Long tenantId, Long userId, UserResourceTreeReq req);

    /**
     * 获取用户有效权限码聚合（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 不分页、扁平 {@code resourceTypeCode:operationCode} 字符串集；可供前端 hasPerms、
     * 功能开关、客户端能力下发等场景使用。
     * 与 {@link #getEffectivePermissions} 的差异：
     * <ul>
     *   <li>本接口不分页 —— 大权限用户的所有有效权限码均会被返回，杜绝截断风险</li>
     *   <li>不携带来源角色、scopeAll、resource 实例等管理面字段</li>
     *   <li>必须在 req.resourceTypeCodes 显式声明白名单，避免下发无关资源类型</li>
     * </ul>
     *
     * @param tenantId 租户ID
     * @param req      有效权限码聚合请求
     * @return 有效权限码响应（perm 串列表）
     */
    UserEffectivePermissionCodesResp getEffectivePermissionCodes(Long tenantId, UserEffectivePermissionCodesReq req);

    /**
     * 获取用户在指定资源类型上的有效资源实例访问事实（T-ACCESS-006 菜单派生公式用）。
     * <p>
     * 与 {@link #getEffectivePermissionCodes} 共享同一 forUserView 管线（相同门禁与过滤），
     * 但返回资源实例粒度：用户在哪些资源类型上有全范围（scopeAll）授权、以及有任意有效
     * 操作码的资源实例 ID 集合。调用方（如菜单可见性派生）据此判定「用户对该资源有任意 op」。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      有效权限查询请求（resourceTypeCodes 白名单为资源类型码）
     * @return 有效资源访问事实（allScopeTypes=全范围资源类型值集合；resourceEntityIds=有任意 op 的资源实例 ID 集合）
     */
    EffectiveResourceAccess getEffectiveResourceAccess(Long tenantId, UserEffectivePermissionCodesReq req);

    /**
     * 有效资源访问事实（resource 实例粒度）。
     *
     * @param allScopeTypes     用户有 scopeAll（全范围）授权的资源类型值集合
     * @param resourceEntityIds 用户有任意有效操作码的资源实例 ID 集合（非 null）
     */
    record EffectiveResourceAccess(Set<Integer> allScopeTypes, Set<Long> resourceEntityIds) {}
}
