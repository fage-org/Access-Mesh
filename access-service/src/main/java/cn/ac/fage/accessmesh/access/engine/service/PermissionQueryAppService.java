package cn.ac.fage.accessmesh.access.engine.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;

/**
 * 权限查询应用服务接口
 * <p>
 * 提供高级查询功能：资源查询、范围查询。
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


}
