package cn.ac.fage.accessmesh.access.auth.service.domain;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import java.util.List;

public interface PlatformAccountDomainService {
    PlatformAccount findByUsername(String username);
    PlatformAccount findById(long id);
    List<PlatformAccount> page(int offset, int limit);
    long count();
    long countEnabled();
    void lockManagement();
    void insert(PlatformAccount account);
    void updateStatus(long id, int status, long operatorId);
    void updatePassword(long id, String hash, boolean forceReset, long operatorId);
    void updateName(long id, String name, long operatorId);
}
