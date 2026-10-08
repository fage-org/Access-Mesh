package cn.ac.fage.accessmesh.access.auth.service.domain.impl;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.mapper.PlatformAccountMapper;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;

@Service
public class PlatformAccountDomainServiceImpl implements PlatformAccountDomainService {
    private final PlatformAccountMapper mapper;
    public PlatformAccountDomainServiceImpl(PlatformAccountMapper mapper) { this.mapper = mapper; }
    public PlatformAccount findByUsername(String username) { return mapper.findByUsername(username); }
    public PlatformAccount findById(long id) { return mapper.findById(id); }
    public List<PlatformAccount> page(int offset, int limit) { return mapper.page(offset, limit); }
    public long count() { return mapper.count(); }
    public long countEnabled() { return mapper.countEnabled(); }
    public void lockManagement() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("platform account management lock requires a transaction");
        }
        mapper.lockManagement();
    }
    public void insert(PlatformAccount account) { mapper.insert(account); }
    public void updateStatus(long id, int status, long operatorId) { mapper.updateStatus(id, status, operatorId); }
    public void updatePassword(long id, String hash, boolean forceReset, long operatorId) {
        mapper.updatePassword(id, hash, forceReset, operatorId);
    }
    public void updateName(long id, String name, long operatorId) { mapper.updateName(id, name, operatorId); }
}
