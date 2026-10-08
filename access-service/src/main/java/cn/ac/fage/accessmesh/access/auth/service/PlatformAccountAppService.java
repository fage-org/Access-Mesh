package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.auth.dto.PlatformStatusReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreateReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountCreatedResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountNameReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountResp;
import cn.ac.fage.accessmesh.access.auth.dto.IssuedPasswordResp;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface PlatformAccountAppService {
    void updateStatus(PlatformStatusReq req);
    PageResp<PlatformAccountResp> page(PageReq req);
    PlatformAccountCreatedResp create(PlatformAccountCreateReq req);
    void updateName(PlatformAccountNameReq req);
    IssuedPasswordResp resetPassword(long accountId);
}
