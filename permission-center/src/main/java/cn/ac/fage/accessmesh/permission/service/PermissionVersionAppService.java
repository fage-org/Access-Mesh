package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionVersionQueryReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionVersionResp;

/**
 * 权限版本服务接口
 * <p>
 * 提供权限版本查询功能，用于Gateway检查缓存快照是否过期。
 * 当角色权限配置变更时，版本号会递增。
 * </p>
 */
public interface PermissionVersionAppService {

    /**
     * 查询角色当前权限版本号
     * <p>
     * 根据业务键查询角色的权限版本号。
     * 版本号在角色权限配置变更时递增。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      版本查询请求，包含角色类型编码和外部标识
     * @return 权限版本响应，包含版本号和角色信息
     */
    PermissionVersionResp queryVersion(Long tenantId, PermissionVersionQueryReq req);
}