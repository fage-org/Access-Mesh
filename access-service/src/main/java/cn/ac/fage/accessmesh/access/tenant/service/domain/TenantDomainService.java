package cn.ac.fage.accessmesh.access.tenant.service.domain;

import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import java.util.List;

public interface TenantDomainService {
    SysTenant findById(long id);
    SysTenant findByCode(String code);
    boolean codeExists(String code);
    void lock(long id);
    List<Long> lockBatch(List<Long> ids);
    List<SysTenant> findBatch(List<Long> ids);
    List<SysTenant> scan(long afterId, int limit);
    List<SysTenant> page(String keyword, int offset, int limit);
    long count(String keyword);
    void insert(SysTenant tenant);
    void attachAdmin(long id, long adminId);
    void updateName(long id, String name, long actorId);
    void updateStatus(long id, int status, long actorId);
}
