package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.bootstrap.mapper.TenantBaselineMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TenantBaselineInitializer {
    private final TenantBaselineMapper mapper;

    public TenantBaselineInitializer(TenantBaselineMapper mapper) {
        this.mapper = mapper;
    }

    public void initialize(long tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("tenant baseline requires opening transaction");
        }
        mapper.initialize(tenantId);
    }
}
