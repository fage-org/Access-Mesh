package cn.ac.fage.accessmesh.access.tenant.service.impl;

import cn.ac.fage.accessmesh.access.tenant.service.TenantGateRepairAppService;
import cn.ac.fage.accessmesh.access.tenant.service.TenantGateUnavailableException;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;

@Service
public class TenantGateRepairAppServiceImpl implements TenantGateRepairAppService {
    private final TenantDomainService tenants;
    private final RedisTenantGateStore gates;

    public TenantGateRepairAppServiceImpl(TenantDomainService tenants, RedisTenantGateStore gates) {
        this.tenants = tenants;
        this.gates = gates;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void repair(List<Long> tenantIds) {
        List<Long> locked = tenants.lockBatch(tenantIds);
        if (locked.isEmpty()) return;
        var current = gates.readBatch(locked);
        var pending = locked.stream()
            .filter(id -> current.get(id).status() == TenantGateState.Status.UNAVAILABLE)
            .toList();
        if (pending.isEmpty()) return;
        var publications = new LinkedHashMap<Long, RedisTenantGateStore.Publication>();
        for (Long id : pending) publications.put(id, gates.reserve(id));
        // 行锁后预阻断，再一次批量读取最新事实；不按租户循环查询数据库。
        var facts = tenants.findBatch(pending);
        for (var row : facts) {
            var state = new TenantGateState(
                row.getStatus() == 1 ? TenantGateState.Status.ENABLED : TenantGateState.Status.DISABLED,
                row.getSessionEpoch());
            if (!gates.publish(publications.get(row.getId()), state)) throw new TenantGateUnavailableException();
        }
    }
}
