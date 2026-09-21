package cn.ac.fage.accessmesh.access.resource.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;

/**
 * 独立依赖 manifest 发布应用服务（T-PERM-071）：服务身份入口（M2M 凭证/内部密钥，无用户门禁），
 * 租户取认证上下文；编译、发布代次与图替换的事务边界见实现。
 */
public interface PermissionManifestAppService {
    SyncResultResp fullSync(Long tenantId, PermissionManifestReq request);
}
