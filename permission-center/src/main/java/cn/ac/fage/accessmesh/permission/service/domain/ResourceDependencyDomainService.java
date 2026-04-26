package cn.ac.fage.accessmesh.permission.service.domain;

public interface ResourceDependencyDomainService {

    void processDependencies(Long tenantId, Long roleId, Long resourceEntityId, Long operationBits);

    void cleanupDependencies(Long tenantId, Long roleId, Long resourceEntityId);
}
