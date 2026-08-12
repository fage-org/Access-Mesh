package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionTreeReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.QueryScopesResp;

/**
 * 权限查询应用服务接口
 * <p>
 * 提供高级查询功能：资源查询、范围查询、权限树查询、接口快照。
 * 从 PermissionServiceImpl 提取。
 * </p>
 */
public interface PermissionQueryAppService {

    /**
     * 查询可访问资源
     *
     * @param tenantId 租户ID
     * @param req      资源查询请求
     * @return 可访问资源响应
     */
    QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req);

    /**
     * 查询范围资源
     *
     * @param tenantId 租户ID
     * @param req      范围查询请求
     * @return 范围资源响应
     */
    QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req);

    /**
     * 接口快照查询
     *
     * @param tenantId 租户ID
     * @param req      接口快照请求
     * @return 接口快照响应
     */
    InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req);

    /**
     * 查询权限树
     *
     * @param tenantId 租户ID
     * @param req      权限树查询请求
     * @return 权限树响应
     */
    PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req);
}
