package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService.AssociationGrant;

import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceAssociationOwnerReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq;
import java.util.List;
import java.util.Set;

/** 同一资源同步事务内的关系预校验输入与结果；结果不跨事务缓存。 */
public final class ResourceAssociationPublication {
    private ResourceAssociationPublication() {}

    public record Input(String operation, ResourceKeyReq resource, String sourceEntityId,
                        ResourceAssociationOwnerReq owner, List<String> referencedTypes, ResourceKeyReq treeParent) {
        public Input {
            resource = normalized(resource);
            treeParent = treeParent == null ? null : normalized(treeParent);
        }
        private static ResourceKeyReq normalized(ResourceKeyReq value) {
            return new ResourceKeyReq(value.resourceTypeCode(), value.code(), value.normalizedCodeType());
        }
    }

    public record Plan(String retryClass, String reason, boolean identityRequired, String sourceEntityId,
                       Long ownerId, boolean createOwnership, Set<Long> addedTypeIds, Set<Long> removedTypeIds,
                       boolean matchesExisting,
                       List<AssociationGrant> removedGrants) {
        public Plan { removedGrants = List.copyOf(removedGrants); }
        public Plan(String retryClass, String reason, boolean identityRequired, String sourceEntityId,
                    Long ownerId, boolean createOwnership, Set<Long> addedTypeIds, Set<Long> removedTypeIds, boolean matchesExisting) {
            this(retryClass, reason, identityRequired, sourceEntityId, ownerId, createOwnership, addedTypeIds, removedTypeIds, matchesExisting, List.of());
        }
        public Plan withRemovedGrants(List<AssociationGrant> rows) {
            return new Plan(retryClass, reason, identityRequired, sourceEntityId, ownerId, createOwnership, addedTypeIds, removedTypeIds, matchesExisting, rows);
        }
        public static final Plan NONE = new Plan(null, null, false, null, null, false, Set.of(), Set.of(), true);
        public boolean valid() { return retryClass == null; }
        public static Plan failure(String retryClass, String reason) {
            return new Plan(retryClass, reason, false, null, null, false, Set.of(), Set.of(), false);
        }
    }
}
