package cn.ac.fage.accessmesh.access.engine.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;

/**
 * 权限检查应用服务接口
 * <p>
 * 提供纯校验功能：单次校验、批量校验。
 * 从 PermissionServiceImpl 提取；T-PERM-089/090 起入口全部经 QueryExecutionEngine.execute。
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

}
