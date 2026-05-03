package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceDependencyResp;

import java.util.List;

/**
 * Resource dependency management service.
 */
public interface DependencyManageService {

    ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId);

    List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId);

    List<ResourceDependencyResp> listAllDependencies(Long tenantId);

    ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId);

    boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req);

    void deleteDependency(Long tenantId, Long dependencyId, Long operatorId);

    void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId);

    void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId);
}