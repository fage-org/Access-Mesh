package cn.ac.fage.accessmesh.access.audit.service;

import cn.ac.fage.accessmesh.common.model.GatewayDenialAuditReq;

public interface GatewayAuditAppService {
    void recordDenial(GatewayDenialAuditReq request);
}
