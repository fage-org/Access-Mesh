package cn.ac.fage.accessmesh.access.tenant.service;

import cn.ac.fage.accessmesh.access.tenant.dto.TenantAdminPasswordResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantCreateReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantCreatedResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantNameReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantPageReq;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantResp;
import cn.ac.fage.accessmesh.access.tenant.dto.TenantStatusReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface TenantAppService {
    PageResp<TenantResp> page(TenantPageReq req);
    TenantResp detail(long id);
    TenantCreatedResp create(TenantCreateReq req);
    void updateName(TenantNameReq req);
    TenantResp updateStatus(TenantStatusReq req);
    TenantAdminPasswordResp resetAdminPassword(long id);
}
