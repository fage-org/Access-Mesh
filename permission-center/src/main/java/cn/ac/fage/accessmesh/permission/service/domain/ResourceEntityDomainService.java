package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.util.List;

public interface ResourceEntityDomainService {

    List<ResourceEntity> listByParentId(Long tenantId, Long parentId);

    List<ResourceEntity> listByType(Long tenantId, Integer resourceType);

    void deleteWithChildren(Long tenantId, Long resourceId);
}
