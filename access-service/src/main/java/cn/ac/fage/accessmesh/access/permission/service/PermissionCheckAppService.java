package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp;

/**
 * 权限检查应用服务接口
 * <p>
 * 提供纯校验功能：单次校验、批量校验、接口级校验。
 * 从 PermissionServiceImpl 提取，使用 PermQueryEngine 作为统一查询入口。
 * </p>
 */
public interface PermissionCheckAppService {

    /**
     * 单次权限校验
     *
     * @param tenantId 租户ID
     * @param req      权限校验请求
     * @return 权限校验响应
     */
    AuthCheckResp check(Long tenantId, AuthCheckReq req);

    /**
     * 批量权限校验
     *
     * @param tenantId 租户ID
     * @param req      批量权限校验请求
     * @return 批量权限校验响应
     */
    BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req);

    /**
     * 接口级权限校验（Gateway回调）
     *
     * @param tenantId 租户ID
     * @param req      接口检查请求
     * @return 接口检查响应
     */
    CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req);
}
