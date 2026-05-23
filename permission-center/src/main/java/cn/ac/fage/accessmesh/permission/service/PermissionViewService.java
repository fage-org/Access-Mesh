package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserEffectiveRolesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.permission.dto.resp.EffectiveRoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionRecentChangesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;

/**
 * 权限视图服务接口
 * <p>
 * 提供只读的权限可见性API，用于查看用户、资源、角色的权限信息。
 * 支持权限解释、最近变更查询、有效角色列表等功能。
 * </p>
 */
public interface PermissionViewService {

    /**
     * 获取用户的有效权限（带筛选和分页）
     * <p>
     * 查看用户的有效权限，支持按条件筛选和分页。
     * 返回统一的权限项类型。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户权限视图请求，包含筛选条件和分页参数
     * @return 有效权限响应
     */
    PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req);

    /**
     * 获取资源的权限信息
     * <p>
     * 查看哪些角色对指定资源拥有权限。
     * </p>
     *
     * @param tenantId         租户ID
     * @param domainCode       业务域编码
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型（external_id或code）
     * @return 资源权限视图响应
     */
    ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType);

    /**
     * 获取角色的权限信息
     * <p>
     * 查看角色拥有的权限，返回扁平列表。
     * 可选择是否展开显示子权限。
     * </p>
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
     * <p>
     * 详细解释为什么用户对指定资源操作拥有或没有权限。
     * 包括权限来源、计算路径等信息。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限解释请求
     * @return 权限解释响应
     */
    PermissionExplainResp explain(Long tenantId, PermissionExplainReq req);

    /**
     * 查询最近的权限变更
     * <p>
     * 获取指定范围内最近发生的权限变更记录。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      最近变更查询请求
     * @return 最近变更响应
     */
    PermissionRecentChangesResp recentChanges(Long tenantId, PermissionRecentChangesReq req);

    /**
     * 获取用户的有效角色列表
     * <p>
     * 查询用户在指定范围内拥有的所有有效角色。
     * 包括直接分配的角色和通过分组继承的角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      有效角色查询请求
     * @return 有效角色列表响应
     */
    ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req);

    /**
     * 获取用户的资源权限树
     * <p>
     * 返回用户可访问的资源树结构。
     * 用于前端展示用户的权限范围。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param req      用户资源树查询请求
     * @return 资源权限树响应列表
     */
    java.util.List<ResourcePermissionTreeResp> getUserResourceTree(Long tenantId, Long userId, UserResourceTreeReq req);
}