package cn.ac.fage.accessmesh.access.resource.service.domain;

import java.util.Set;
import java.util.Collection;
import java.util.List;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceAssociation;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq;
import java.util.Map;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceAssociationDeclaration.Mode;

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
    record CandidateScope(Long primaryId, TypeDefinition type, Mode mode) {}
    record OwnerView(Mode mode, ResourceEntity owner, String ownerTypeCode) {
        public static final OwnerView NONE = new OwnerView(Mode.NONE, null, null);
    }
    record DetailView(OwnerView ownerView, List<String> referencedTypeCodes, long exclusiveCount,
                      long referencingCount, String sourceService) {}
    record TypeCandidates(Set<String> knownCodes, Set<String> excludedCodes) {}
    CandidateScope candidateScope(Long tenantId, ResourceKeyReq parent, String childTypeCode);
    Map<Long, OwnerView> describeOwners(Long tenantId, List<ResourceEntity> entities);
    DetailView describeResource(Long tenantId, ResourceEntity entity);
    TypeCandidates candidateTypes(Long tenantId, String parentTypeCode, ResourceKeyReq parent);
}
