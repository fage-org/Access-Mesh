package cn.ac.fage.accessmesh.access.resource.service.domain;

import java.util.Set;
import java.util.Collection;
import java.util.List;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceAssociation;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq;
import java.util.Map;

/** 调用方声明事务；关系写入复用资源树锁，实例与关系须同事务建立。 */
public interface ResourceAssociationDomainService {
    List<ResourceAssociation> findByPrimaryIds(Long tenantId, Collection<Long> primaryIds);
    List<ResourceAssociation> findByExclusiveIds(Long tenantId, Collection<Long> exclusiveIds);
    void assertTypeChangeAllowed(Long tenantId, Long typeId, String newExtra);
    void assertTypesDeletable(Long tenantId, Set<Long> typeIds);
    Set<Long> expandResourceDeletion(Long tenantId, Set<Long> resourceIds);
    void removeResourceAssociations(Long tenantId, Set<Long> resourceIds, Long actorId);
    boolean isAssociationAwareType(Long tenantId, String resourceTypeCode);
    Map<ResourceKeyReq, ResourceAssociationPublication.Plan> preparePublications(Long tenantId, String sourceService,
        List<ResourceAssociationPublication.Input> inputs);
    void applyPublication(Long tenantId, Long resourceId, ResourceAssociationPublication.Plan plan, Long actorId);
}
