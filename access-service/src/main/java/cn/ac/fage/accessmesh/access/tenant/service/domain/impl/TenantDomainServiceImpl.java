package cn.ac.fage.accessmesh.access.tenant.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import cn.ac.fage.accessmesh.access.tenant.mapper.TenantMapper;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class TenantDomainServiceImpl implements TenantDomainService {
    private final TenantMapper mapper;

    public TenantDomainServiceImpl(TenantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SysTenant findById(long id) { return mapper.findById(id); }

    @Override
    public SysTenant findByCode(String code) { return mapper.findByCode(code); }

    @Override
    public boolean codeExists(String code) { return mapper.countCode(code) > 0; }

    @Override
    public void lock(long id) {
        requireTransaction();
        if (mapper.lock(id) == null) {
            throw new BizException(AccessErrorCode.TENANT_NOT_FOUND.getCode(), AccessErrorCode.TENANT_NOT_FOUND.getMessage());
        }
    }

    @Override
    public List<Long> lockBatch(List<Long> ids) {
        requireTransaction();
        return ids.isEmpty() ? List.of() : mapper.lockBatch(ids);
    }

    @Override
    public List<SysTenant> findBatch(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findBatch(ids);
    }

    @Override
    public List<SysTenant> scan(long afterId, int limit) { return mapper.scan(afterId, limit); }

    @Override
    public List<SysTenant> page(String keyword, int offset, int limit) { return mapper.page(keyword, offset, limit); }

    @Override
    public long count(String keyword) { return mapper.count(keyword); }

    @Override
    public void insert(SysTenant tenant) { mapper.insert(tenant); }

    @Override
    public void attachAdmin(long id, long adminId) { mapper.attachAdmin(id, adminId); }

    @Override
    public void updateName(long id, String name, long actorId) { mapper.updateName(id, name, actorId); }

    @Override
    public void updateStatus(long id, int status, long actorId) { mapper.updateStatus(id, status, actorId); }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("tenant lock requires transaction");
        }
    }
}
