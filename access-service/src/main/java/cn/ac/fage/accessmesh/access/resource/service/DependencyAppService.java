package cn.ac.fage.accessmesh.access.resource.service;

import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceDependencyResp;
import java.util.List;

/** 编译依赖图的只读管理查询；写入仅由 MANIFEST 编译器承担。 */
public interface DependencyAppService {
    List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId);
    List<ResourceDependencyResp> listAllDependencies(Long tenantId);
    boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req);
}
