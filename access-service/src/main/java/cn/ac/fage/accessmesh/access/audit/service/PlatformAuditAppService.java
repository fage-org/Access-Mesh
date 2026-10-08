package cn.ac.fage.accessmesh.access.audit.service;

import cn.ac.fage.accessmesh.access.audit.dto.req.PlatformAuditPageReq;
import cn.ac.fage.accessmesh.access.audit.dto.resp.PlatformAuditResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;

public interface PlatformAuditAppService {
    PageResp<PlatformAuditResp> page(PlatformAuditPageReq req);
}
