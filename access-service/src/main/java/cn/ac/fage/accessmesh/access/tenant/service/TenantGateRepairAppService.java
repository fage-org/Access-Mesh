package cn.ac.fage.accessmesh.access.tenant.service;

import java.util.List;

public interface TenantGateRepairAppService {
    void repair(List<Long> tenantIds);
}
