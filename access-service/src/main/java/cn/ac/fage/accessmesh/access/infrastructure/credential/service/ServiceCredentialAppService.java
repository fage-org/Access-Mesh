package cn.ac.fage.accessmesh.access.infrastructure.credential.service;

import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialCreateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialListReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialUpdateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialCreateResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;

/**
 * 服务凭证管理应用服务（T-PERM-070，service-authentication.md §3.4）。
 * <p>门禁挂 service-config 管理面同族：写操作 SERVICE:MANAGE、list SERVICE:VIEW
 * （bootstrap 固定图已有授权，零新增）。轮换流程=签发新凭证（并存）→ 分发 →
 * 验证生效 → update 停旧。</p>
 */
public interface ServiceCredentialAppService {

    /** 签发凭证（响应含明文 secret，仅此一次）。 */
    ServiceCredentialCreateResp create(Long tenantId, ServiceCredentialCreateReq req, Long operatorId);

    /** 更新凭证（status 启停 / expiresAt 改期；secret 与 credential_id 不可改）。 */
    ServiceCredentialResp update(Long tenantId, ServiceCredentialUpdateReq req, Long operatorId);

    /** 软删除凭证。 */
    void remove(Long tenantId, Long id, Long operatorId);

    /** 凭证列表（serviceCode 可选过滤，含停用/过期行——轮换状态可见）。 */
    ItemsResp<ServiceCredentialResp> list(Long tenantId, ServiceCredentialListReq req);
}
