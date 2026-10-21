package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantMaterializationDomainService;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceAssociation;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceAssociationMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceAssociationDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceAssociationRuntime;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceAssociationPublication.Input;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceAssociationPublication.Plan;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceAssociationDeclaration;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceAssociationDeclaration.Mode;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ResourceAssociationDomainServiceImpl implements ResourceAssociationDomainService {
    private final ResourceAssociationMapper relations;
    private final ResourceEntityMapper resources;
    private final TypeDefinitionDomainService types;
    private final ServiceConfigDomainService services;
    private final ResourceTypeOwnershipGuard ownership;
    private final TreeWriteLockSupport locks;
    private final ObjectMapper json;
    private final RoleResourcePermissionDomainService grants;
    private final PermissionConditionDomainService conditions;
    private final AutoGrantMaterializationDomainService autoGrants;
    private final AuditDomainService audit;

    public ResourceAssociationDomainServiceImpl(ResourceAssociationMapper relations, ResourceEntityMapper resources,
            TypeDefinitionDomainService types, ServiceConfigDomainService services, ResourceTypeOwnershipGuard ownership,
            TreeWriteLockSupport locks, ObjectMapper json, RoleResourcePermissionDomainService grants,
            PermissionConditionDomainService conditions,
            AutoGrantMaterializationDomainService autoGrants,
            AuditDomainService audit) {
        this.relations = relations;
        this.resources = resources;
        this.types = types;
        this.services = services;
        this.ownership = ownership;
        this.locks = locks;
        this.json = json;
        this.grants = grants;
        this.conditions = conditions;
        this.autoGrants = autoGrants;
        this.audit = audit;
    }

    @Override
    public List<ResourceAssociation> findByPrimaryIds(Long tenantId, Collection<Long> primaryIds) {
        List<ResourceAssociation> result = new ArrayList<>();
        SqlBatches.forEach(new ArrayList<>(primaryIds), ids -> result.addAll(relations.selectByPrimaryIds(tenantId, ids)));
        return result;
    }

    @Override
    public List<ResourceAssociation> findByExclusiveIds(Long tenantId, Collection<Long> exclusiveIds) {
        List<ResourceAssociation> result = new ArrayList<>();
        SqlBatches.forEach(new ArrayList<>(exclusiveIds), ids -> result.addAll(relations.selectByExclusiveIds(tenantId, ids)));
        return result;
    }

    @Override
    public void assertTypeChangeAllowed(Long tenantId, Long typeId, String newExtra) {
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        var current = types.selectValidByIds(tenantId, Set.of(typeId));
        if (current.isEmpty()) throw new BizException(AccessErrorCode.RESOURCE_TYPE_NOT_FOUND.getCode(), "类型不存在");
        TypeDefinition type = current.getFirst();
        if (!"resource_type".equals(type.getTypeKey())) return;
        ResourceAssociationDeclaration next;
        try { next = ResourceAssociationDeclaration.parse(json, newExtra); }
        catch (IllegalArgumentException exception) { throw invalid(exception.getMessage()); }
        boolean associationChanged = !declaration(type).equals(next);
        boolean ownershipChanged = !ownership.parseOwnership(type.getExtra()).equals(ownership.parseOwnership(newExtra));
        if (!associationChanged && !ownershipChanged) return;
        if (ownershipChanged || next.mode() != Mode.NONE) {
            for (TypeDefinition other : types.selectByTenantAndTypeKey(tenantId, "resource_type")) {
                if (!Objects.equals(other.getId(), typeId) && declaration(other).parentTypeCodes().contains(type.getTypeCode())) {
                    throw conflict("类型仍作为关联类型的主类型，不能改变维护来源或启用递归关系: " + other.getTypeCode());
                }
            }
        }
        if (!associationChanged) return;
        if (Boolean.TRUE.equals(type.getIsSystem()) || resources.existsValidByType(tenantId, type.getTypeValue()) > 0
            || relations.hasTypeUses(tenantId, type.getId(), type.getTypeValue())
            || grants.hasBusinessGrantsForTypes(tenantId, Set.of(type.getTypeValue()))) {
            throw conflict("类型已有资源、关系或业务授权，不允许改变关系用途");
        }
    }

    @Override
    public void assertTypesDeletable(Long tenantId, Set<Long> typeIds) {
        if (typeIds.isEmpty()) return;
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        List<TypeDefinition> targets = new ArrayList<>();
        SqlBatches.forEach(new ArrayList<>(typeIds), ids -> targets.addAll(types.selectValidByIds(tenantId, Set.copyOf(ids))));
        Set<String> codes = targets.stream().filter(type -> "resource_type".equals(type.getTypeKey()))
            .map(TypeDefinition::getTypeCode).collect(Collectors.toSet());
        if (codes.isEmpty()) return;
        SqlBatches.forEach(new ArrayList<>(typeIds), ids -> {
            if (!relations.selectBySharedTypeIds(tenantId, ids).isEmpty()) throw conflict("通用类型仍被主资源引用，请先解除引用");
        });
        for (TypeDefinition other : types.selectByTenantAndTypeKey(tenantId, "resource_type")) {
            if (!typeIds.contains(other.getId()) && declaration(other).parentTypeCodes().stream().anyMatch(codes::contains)) {
                throw conflict("类型仍被允许主类型声明引用: " + other.getTypeCode());
            }
        }
    }

    @Override
    public Set<Long> expandResourceDeletion(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds.isEmpty()) return Set.of();
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        Set<Long> result = new HashSet<>(resourceIds);
        findByPrimaryIds(tenantId, resourceIds).stream().map(ResourceAssociation::getExclusiveResourceId)
            .filter(Objects::nonNull).forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    public void removeResourceAssociations(Long tenantId, Set<Long> resourceIds, Long actorId) {
        if (resourceIds.isEmpty()) return;
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        LocalDateTime now = LocalDateTime.now();
        SqlBatches.forEach(new ArrayList<>(resourceIds), ids -> relations.softDeleteByResourceIds(tenantId, ids, actorId, now));
        var removed = grants.selectAssociationGrants(tenantId, resourceIds, true);
        removeGrants(tenantId, removed, now, "RESOURCE_DELETED");
        if (!removed.isEmpty()) PermissionChangeContext.markRoles(tenantId,
            autoGrants.recompute(tenantId, removed.stream().map(RoleResourcePermissionDomainService.AssociationGrant::roleId).collect(Collectors.toSet()), Set.of()));
        recycleConditions(tenantId, removed);
    }

    @Override
    public boolean isAssociationAwareType(Long tenantId, String resourceTypeCode) {
        List<TypeDefinition> directory = types.selectByTenantAndTypeKey(tenantId, "resource_type");
        TypeDefinition target = null;
        for (TypeDefinition type : directory) {
            if (Objects.equals(type.getTypeCode(), resourceTypeCode)) {
                target = type;
                if (declaration(type).mode() != Mode.NONE) return true;
            }
            if (declaration(type).parentTypeCodes().contains(resourceTypeCode)) return true;
        }
        return target != null && resources.hasSourceIdentities(tenantId, target.getTypeValue());
    }

    private record EntityKey(Integer type, String code, String codeType) {}
    private record PublicationFacts(Map<String, TypeDefinition> typesByCode, Map<Integer, TypeDefinition> typesByValue,
            Map<EntityKey, ResourceEntity> entities, Map<Long, ResourceEntity> entitiesById,
            Map<Long, Long> owners, Map<Long, Set<Long>> references, Map<EntityKey, Set<String>> retiredIdentities,
            boolean sourceEnabled) {}

    @Override
    public Map<ResourceKeyReq, Plan> preparePublications(Long tenantId, String source, List<Input> inputs) {
        if (inputs.isEmpty()) return Map.of();
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        Map<String, TypeDefinition> byCode = types.selectByTenantAndTypeKey(tenantId, "resource_type").stream()
            .collect(Collectors.toMap(TypeDefinition::getTypeCode, type -> type));
        Map<Integer, TypeDefinition> byValue = byCode.values().stream().collect(Collectors.toMap(TypeDefinition::getTypeValue, type -> type));
        Set<ResourceKeyReq> keys = new HashSet<>();
        for (Input input : inputs) {
            keys.add(input.resource());
            if (input.treeParent() != null) keys.add(input.treeParent());
            if (input.owner() != null) keys.add(new ResourceKeyReq(input.owner().resourceTypeCode(), input.owner().code(), input.owner().normalizedCodeType()));
        }
        Set<Integer> values = keys.stream().map(ResourceKeyReq::resourceTypeCode).map(byCode::get).filter(Objects::nonNull)
            .map(TypeDefinition::getTypeValue).collect(Collectors.toSet());
        Set<String> codes = keys.stream().map(ResourceKeyReq::code).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> codeTypes = keys.stream().map(ResourceKeyReq::normalizedCodeType).collect(Collectors.toSet());
        Map<EntityKey, ResourceEntity> current = new HashMap<>();
        Map<EntityKey, Set<String>> retired = new HashMap<>();
        if (!values.isEmpty() && !codes.isEmpty()) {
            SqlBatches.forEach(new ArrayList<>(codes), batch -> {
                for (ResourceEntity row : resources.selectByTypesAndCodesAndCodeTypes(tenantId, values, Set.copyOf(batch), codeTypes)) {
                    current.put(entityKey(row), row);
                }
                for (ResourceEntity row : resources.selectSourceIdentityHistory(tenantId, values, Set.copyOf(batch), codeTypes)) {
                    retired.computeIfAbsent(entityKey(row), ignored -> new HashSet<>()).add(row.getSourceEntityId());
                }
            });
        }
        Map<Long, ResourceEntity> byId = current.values().stream().collect(Collectors.toMap(ResourceEntity::getId, row -> row));
        Map<Long, Long> owners = findByExclusiveIds(tenantId, byId.keySet()).stream()
            .collect(Collectors.toMap(ResourceAssociation::getExclusiveResourceId, ResourceAssociation::getPrimaryResourceId));
        Set<Long> absentOwners = new HashSet<>(owners.values());
        absentOwners.removeAll(byId.keySet());
        SqlBatches.forEach(new ArrayList<>(absentOwners), ids -> resources.selectValidByIds(tenantId, Set.copyOf(ids))
            .forEach(row -> byId.put(row.getId(), row)));
        Map<Long, Set<Long>> references = new HashMap<>();
        for (ResourceAssociation row : findByPrimaryIds(tenantId, byId.keySet())) {
            if (row.getSharedTypeId() != null) references.computeIfAbsent(row.getPrimaryResourceId(), ignored -> new HashSet<>()).add(row.getSharedTypeId());
        }
        boolean enabled = source != null && !LocalProjectionOwner.isInternalSourceService(source)
            && ServiceConfigDomainService.isRegisteredAndEnabled(services.selectByTenantAndServiceCode(tenantId, source));
        PublicationFacts facts = new PublicationFacts(byCode, byValue, current, byId, owners, references, retired, enabled);
        Map<ResourceKeyReq, Plan> result = new LinkedHashMap<>();
        for (Input input : inputs) result.put(input.resource(), preparePublication(source, input, facts));
        Map<ResourceKeyReq, Long> detachedPrimaries = new HashMap<>();
        result.forEach((key, plan) -> {
            if (plan.valid() && !plan.removedTypeIds().isEmpty()) detachedPrimaries.put(key,
                current.get(new EntityKey(byCode.get(key.resourceTypeCode()).getTypeValue(), key.code(), key.normalizedCodeType())).getId());
        });
        if (!detachedPrimaries.isEmpty()) {
            var childGrants = grants.selectAssociationGrants(tenantId, Set.copyOf(detachedPrimaries.values()), false);
            Map<Long, Integer> typeValues = byCode.values().stream().collect(Collectors.toMap(TypeDefinition::getId, TypeDefinition::getTypeValue));
            detachedPrimaries.forEach((key, primaryId) -> {
                Plan plan = result.get(key);
                Set<Integer> removedValues = plan.removedTypeIds().stream().map(typeValues::get).filter(Objects::nonNull).collect(Collectors.toSet());
                result.put(key, plan.withRemovedGrants(childGrants.stream().filter(row -> primaryId.equals(row.primaryId())
                    && removedValues.contains(row.resourceType())).toList()));
            });
        }
        return Map.copyOf(result);
    }

    private Plan preparePublication(String source, Input input, PublicationFacts facts) {
        TypeDefinition type = facts.typesByCode().get(input.resource().resourceTypeCode());
        if (type == null) return missing("RESOURCE_TYPE_NOT_FOUND");
        if (!facts.sourceEnabled() || !ownership.parseOwnership(type.getExtra()).syncOwnedBy(source)) {
            return Plan.failure("SECURITY_DENIED", "RESOURCE_TYPE_OWNERSHIP_DENIED");
        }
        ResourceAssociationDeclaration declared = declaration(type);
        EntityKey key = new EntityKey(type.getTypeValue(), input.resource().code(), input.resource().normalizedCodeType());
        ResourceEntity current = facts.entities().get(key);
        String identity = input.sourceEntityId();
        if (identity == null || identity.isBlank() || identity.length() > 256 || !identity.equals(identity.trim())
            || current != null && current.getSourceEntityId() != null && !identity.equals(current.getSourceEntityId())
            || "UPSERT".equals(input.operation()) && current == null && facts.retiredIdentities().getOrDefault(key, Set.of()).contains(identity)) {
            return identityConflict();
        }
        if (!"UPSERT".equals(input.operation())) {
            return new Plan(null, null, true, identity, null, false, Set.of(), Set.of(), true);
        }
        Long ownerId = null;
        boolean createOwnership = false;
        if (declared.mode() == Mode.EXCLUSIVE) {
            Long previousOwner = current == null ? null : facts.owners().get(current.getId());
            ResourceEntity owner;
            if (input.owner() != null) {
                if (!declared.parentTypeCodes().contains(input.owner().resourceTypeCode())) return invalidPublication();
                TypeDefinition ownerType = facts.typesByCode().get(input.owner().resourceTypeCode());
                if (ownerType == null) return missing("ASSOCIATION_OWNER_NOT_FOUND");
                if (declaration(ownerType).mode() != Mode.NONE || !declared.parentTypeCodes().contains(ownerType.getTypeCode())) return invalidPublication();
                if (!ownership.parseOwnership(ownerType.getExtra()).syncOwnedBy(source)) {
                    return Plan.failure("SECURITY_DENIED", "RESOURCE_TYPE_OWNERSHIP_DENIED");
                }
                owner = facts.entities().get(new EntityKey(ownerType.getTypeValue(), input.owner().code(), input.owner().normalizedCodeType()));
                if (owner == null) return missing("ASSOCIATION_OWNER_NOT_FOUND");
                if (owner.getSourceEntityId() == null) return missing("ASSOCIATION_OWNER_NOT_READY");
                if (!Objects.equals(owner.getSourceEntityId(), input.owner().sourceEntityId())) return identityConflict();
            } else {
                owner = previousOwner == null ? null : facts.entitiesById().get(previousOwner);
                if (owner == null) return invalidPublication();
                if (owner.getSourceEntityId() == null) return missing("ASSOCIATION_OWNER_NOT_READY");
            }
            TypeDefinition ownerType = facts.typesByValue().get(owner.getResourceType());
            if (ownerType == null || declaration(ownerType).mode() != Mode.NONE
                || !declared.parentTypeCodes().contains(ownerType.getTypeCode())) return invalidPublication();
            if (!ownership.parseOwnership(ownerType.getExtra()).syncOwnedBy(source)) {
                return Plan.failure("SECURITY_DENIED", "RESOURCE_TYPE_OWNERSHIP_DENIED");
            }
            ownerId = owner.getId();
            if (previousOwner != null && !previousOwner.equals(ownerId)) return invalidPublication();
            createOwnership = previousOwner == null;
            if (input.treeParent() != null) {
                TypeDefinition treeType = facts.typesByCode().get(input.treeParent().resourceTypeCode());
                ResourceEntity treeParent = treeType == null ? null : facts.entities().get(new EntityKey(treeType.getTypeValue(),
                    input.treeParent().code(), input.treeParent().normalizedCodeType()));
                if (treeParent == null) return missing("PARENT_RESOURCE_NOT_FOUND");
                if (!Objects.equals(facts.owners().get(treeParent.getId()), ownerId)) return invalidPublication();
            }
        } else if (input.owner() != null) return invalidPublication();
        Set<Long> added = new HashSet<>();
        Set<Long> removed = new HashSet<>();
        if (input.referencedTypes() != null) {
            if (declared.mode() != Mode.NONE || input.referencedTypes().size() > 200) return invalidPublication();
            Set<Long> desired = new HashSet<>();
            for (String code : input.referencedTypes()) {
                TypeDefinition target = facts.typesByCode().get(code);
                if (target == null) return missing("ASSOCIATION_TYPE_NOT_FOUND");
                ResourceAssociationDeclaration targetDeclaration = declaration(target);
                if (targetDeclaration.mode() != Mode.SHARED || !targetDeclaration.parentTypeCodes().contains(type.getTypeCode())
                    || !ResourceTypeOwnershipGuard.MODE_MANAGED.equals(ownership.parseOwnership(target.getExtra()).managedMode())) return invalidPublication();
                desired.add(target.getId());
            }
            Set<Long> previous = current == null ? Set.of() : facts.references().getOrDefault(current.getId(), Set.of());
            added.addAll(desired); added.removeAll(previous);
            removed.addAll(previous); removed.removeAll(desired);
        }
        boolean matches = current != null && identity.equals(current.getSourceEntityId()) && !createOwnership && added.isEmpty() && removed.isEmpty();
        return new Plan(null, null, true, identity, ownerId, createOwnership, Set.copyOf(added), Set.copyOf(removed), matches);
    }

    @Override
    public void applyPublication(Long tenantId, Long resourceId, Plan plan, Long actorId) {
        if (!plan.valid()) throw invalid("失败的关系预校验结果不可写入");
        if (!plan.identityRequired()) return;
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        LocalDateTime now = LocalDateTime.now();
        if (plan.createOwnership()) relations.insertExclusive(tenantId, plan.ownerId(), resourceId, actorId, now);
        if (!plan.removedTypeIds().isEmpty()) relations.softDeleteShared(tenantId, resourceId, plan.removedTypeIds(), actorId, now);
        if (!plan.addedTypeIds().isEmpty()) relations.insertShared(tenantId, resourceId, plan.addedTypeIds(), actorId, now);
        removeGrants(tenantId, plan.removedGrants(), now, "SHARED_TYPE_UNLINKED");
    }

    private void removeGrants(Long tenantId, List<RoleResourcePermissionDomainService.AssociationGrant> rows, LocalDateTime now, String cause) {
        if (rows.isEmpty()) return;
        PermissionChangeContext.markRoles(tenantId,
            rows.stream().map(RoleResourcePermissionDomainService.AssociationGrant::roleId).collect(Collectors.toSet()));
        SqlBatches.forEach(rows.stream().map(RoleResourcePermissionDomainService.AssociationGrant::id).toList(),
            ids -> grants.softDeleteBatch(tenantId, ids, now));
        List<AuditDomainService.ChangeLogEntry> changes = new ArrayList<>();
        rows.stream().collect(Collectors.groupingBy(RoleResourcePermissionDomainService.AssociationGrant::roleId)).forEach((roleId, removed) -> {
            var snapshot = json.createObjectNode();
            snapshot.put("eventType", "RESOURCE_ASSOCIATION_GRANT_CLEANUP");
            snapshot.put("cause", cause);
            var ids = snapshot.putArray("removedPermissionIds");
            removed.forEach(row -> ids.add(row.id()));
            changes.add(new AuditDomainService.ChangeLogEntry("role_resource_permission", roleId, "ASSOC_CLEANUP",
                null, null, snapshot.toString(), new Long[0], new Long[]{roleId}));
        });
        audit.recordChangeLog(new AuditDomainService.ChangeLogContext(tenantId, AccessRequestContext.getOperatorId(),
            OperatorContext.getRequestId(), AccessRequestContext.getServiceCode() == null
                ? PermConstants.MaintainSource.MANUAL : PermConstants.MaintainSource.SERVICE_SYNC,
            "resource-association-cleanup"), changes);
    }

    private void recycleConditions(Long tenantId, List<RoleResourcePermissionDomainService.AssociationGrant> rows) {
        Set<Long> ids = rows.stream().map(RoleResourcePermissionDomainService.AssociationGrant::conditionId).filter(Objects::nonNull).collect(Collectors.toSet());
        SqlBatches.forEach(new ArrayList<>(ids), batch -> conditions.recycleOrphanInlineConditions(tenantId, Set.copyOf(batch)));
    }

    @Override
    public void finishPublications(Long tenantId, Collection<Plan> plans) {
        recycleConditions(tenantId, plans.stream().flatMap(plan -> plan.removedGrants().stream()).toList());
    }

    private static EntityKey entityKey(ResourceEntity row) { return new EntityKey(row.getResourceType(), row.getCode(), row.getCodeType()); }
    private static Plan missing(String reason) { return Plan.failure("DEPENDENCY_MISSING", reason); }
    private static Plan identityConflict() { return Plan.failure("NON_RETRYABLE", "RESOURCE_ASSOCIATION_IDENTITY_CONFLICT"); }
    private static Plan invalidPublication() { return Plan.failure("NON_RETRYABLE", "RESOURCE_ASSOCIATION_INVALID"); }

    @Override
    public CandidateScope candidateScope(Long tenantId, ResourceKeyReq parent, String childTypeCode) {
        Set<String> requestedTypes = new HashSet<>(List.of(parent.resourceTypeCode(), childTypeCode));
        Map<String, TypeDefinition> definitions = types.selectResourceTypesByCodes(tenantId, requestedTypes).stream()
            .collect(Collectors.toMap(TypeDefinition::getTypeCode, type -> type));
        TypeDefinition primaryType = definitions.get(parent.resourceTypeCode());
        TypeDefinition childType = definitions.get(childTypeCode);
        if (primaryType == null || childType == null) throw new BizException(AccessErrorCode.RESOURCE_TYPE_NOT_FOUND.getCode(), "资源类型不存在");
        ResourceEntity primary = resources.selectByTypeCodeAndCodeType(tenantId, primaryType.getTypeValue(), parent.code(), parent.normalizedCodeType());
        requireActivePrimary(primary);
        ResourceAssociationDeclaration child = declaration(childType);
        if (child.mode() != Mode.NONE) {
            if (!child.parentTypeCodes().contains(primaryType.getTypeCode())) throw missingAssociation("当前主类型不能使用该关联类型");
            if (primary.getSourceEntityId() == null) throw missingAssociation("主资源来源身份尚未发布");
            if (child.mode() == Mode.SHARED && !relations.selectSharedTypeIds(tenantId, primary.getId()).contains(childType.getId())) {
                throw missingAssociation("主资源尚未引用该通用类型");
            }
        }
        return new CandidateScope(primary.getId(), childType, child.mode());
    }

    @Override
    public Map<Long, OwnerView> describeOwners(Long tenantId, List<ResourceEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        Set<Integer> values = entities.stream().map(ResourceEntity::getResourceType).collect(Collectors.toSet());
        Map<Integer, TypeDefinition> definitions = new HashMap<>();
        SqlBatches.forEach(new ArrayList<>(values), batch -> types.selectResourceTypesByValues(tenantId, Set.copyOf(batch))
            .forEach(type -> definitions.put(type.getTypeValue(), type)));
        Map<Long, Mode> modes = new HashMap<>();
        Set<Long> exclusive = new HashSet<>();
        for (ResourceEntity entity : entities) {
            TypeDefinition type = definitions.get(entity.getResourceType());
            if (type == null) throw new BizException(AccessErrorCode.RESOURCE_TYPE_NOT_FOUND.getCode(), "资源所属类型不存在");
            Mode mode = declaration(type).mode();
            if (mode != Mode.NONE) modes.put(entity.getId(), mode);
            if (mode == Mode.EXCLUSIVE) exclusive.add(entity.getId());
        }
        Map<Long, Long> ownerIds = findByExclusiveIds(tenantId, exclusive).stream()
            .collect(Collectors.toMap(ResourceAssociation::getExclusiveResourceId, ResourceAssociation::getPrimaryResourceId));
        Map<Long, ResourceEntity> owners = new HashMap<>();
        SqlBatches.forEach(new ArrayList<>(new HashSet<>(ownerIds.values())), ids -> resources.selectValidByIds(tenantId, Set.copyOf(ids))
            .forEach(owner -> owners.put(owner.getId(), owner)));
        Set<Integer> ownerTypes = owners.values().stream().map(ResourceEntity::getResourceType).collect(Collectors.toSet());
        ownerTypes.removeAll(definitions.keySet());
        SqlBatches.forEach(new ArrayList<>(ownerTypes), batch -> types.selectResourceTypesByValues(tenantId, Set.copyOf(batch))
            .forEach(type -> definitions.put(type.getTypeValue(), type)));
        Map<Long, OwnerView> result = new HashMap<>();
        modes.forEach((id, mode) -> {
            ResourceEntity owner = owners.get(ownerIds.get(id));
            TypeDefinition ownerType = owner == null ? null : definitions.get(owner.getResourceType());
            result.put(id, new OwnerView(mode, owner, ownerType == null ? null : ownerType.getTypeCode()));
        });
        return Map.copyOf(result);
    }

    @Override
    public DetailView describeResource(Long tenantId, ResourceEntity entity) {
        OwnerView view = describeOwners(tenantId, List.of(entity)).getOrDefault(entity.getId(), OwnerView.NONE);
        List<Long> referenceIds = relations.selectSharedTypeIds(tenantId, entity.getId());
        List<TypeDefinition> referenced = types.selectValidByIds(tenantId, Set.copyOf(referenceIds));
        if (referenced.size() != referenceIds.size()) throw conflict("存在指向无效类型的引用");
        List<TypeDefinition> ownTypes = types.selectResourceTypesByValues(tenantId, Set.of(entity.getResourceType()));
        if (ownTypes.isEmpty()) throw new BizException(AccessErrorCode.RESOURCE_TYPE_NOT_FOUND.getCode(), "资源所属类型不存在");
        TypeDefinition type = ownTypes.getFirst();
        return new DetailView(view, referenced.stream().map(TypeDefinition::getTypeCode).sorted().toList(),
            relations.countExclusive(tenantId, entity.getId()), view.mode() == Mode.SHARED ? relations.countReferences(tenantId, type.getId()) : 0,
            ownership.parseOwnership(type.getExtra()).syncSourceService());
    }

    @Override
    public TypeCandidates candidateTypes(Long tenantId, String parentTypeCode, ResourceKeyReq parent) {
        List<TypeDefinition> directory = types.selectByTenantAndTypeKey(tenantId, "resource_type");
        TypeDefinition primaryType = directory.stream().filter(type -> type.getTypeCode().equals(parentTypeCode)).findFirst()
            .orElseThrow(() -> new BizException(AccessErrorCode.RESOURCE_TYPE_NOT_FOUND.getCode(), "主类型不存在"));
        Set<Long> referenced = Set.of();
        if (parent != null) {
            ResourceEntity primary = resources.selectByTypeCodeAndCodeType(tenantId, primaryType.getTypeValue(), parent.code(), parent.normalizedCodeType());
            requireActivePrimary(primary);
            referenced = Set.copyOf(relations.selectSharedTypeIds(tenantId, primary.getId()));
        }
        Set<String> known = new HashSet<>();
        Set<String> excluded = new HashSet<>();
        for (TypeDefinition type : directory) {
            known.add(type.getTypeCode());
            ResourceAssociationDeclaration declared = declaration(type);
            if (declared.mode() == Mode.NONE) continue;
            if (!declared.parentTypeCodes().contains(parentTypeCode)
                || parent != null && declared.mode() == Mode.SHARED && !referenced.contains(type.getId())) excluded.add(type.getTypeCode());
        }
        return new TypeCandidates(Set.copyOf(known), Set.copyOf(excluded));
    }

    private static void requireActivePrimary(ResourceEntity primary) {
        if (primary == null) throw new BizException(AccessErrorCode.RESOURCE_NOT_FOUND.getCode(), "主资源不存在");
        if (!Integer.valueOf(1).equals(primary.getStatus())) throw missingAssociation("主资源已停用");
    }
    private static BizException missingAssociation(String message) { return new BizException(AccessErrorCode.RESOURCE_ASSOCIATION_REQUIRED.getCode(), message); }

    @Override
    public Map<Integer, ResourceAssociationRuntime.TypeRule> runtimeTypes(Long tenantId, Set<Integer> values) {
        Map<Integer, ResourceAssociationRuntime.TypeRule> result = new HashMap<>();
        SqlBatches.forEach(new ArrayList<>(values), batch -> {
            for (TypeDefinition type : types.selectResourceTypeHistoryByValues(tenantId, Set.copyOf(batch))) {
                ResourceAssociationDeclaration declared = declaration(type);
                if (declared.mode() != Mode.NONE) result.put(type.getTypeValue(), new ResourceAssociationRuntime.TypeRule(
                    type.getId(), type.getTypeValue(), type.getTypeCode(), declared.mode(), declared.parentTypeCodes(), Long.valueOf(0).equals(type.getDeleteFlag())));
            }
        });
        return Map.copyOf(result);
    }

    @Override
    public ResourceAssociationRuntime.Context runtimeContext(Long tenantId, String parentTypeCode, Long primaryId,
            Map<Integer, ResourceAssociationRuntime.TypeRule> rules) {
        Set<Integer> applicable = new HashSet<>();
        ResourceEntity primary = primaryId == null ? null : resources.selectValidById(tenantId, primaryId);
        if (primary == null || !Integer.valueOf(1).equals(primary.getStatus()) || primary.getSourceEntityId() == null) {
            return new ResourceAssociationRuntime.Context(primaryId, rules, applicable);
        }
        var primaryTypes = types.selectResourceTypesByValues(tenantId, Set.of(primary.getResourceType()));
        if (primaryTypes.isEmpty() || !primaryTypes.getFirst().getTypeCode().equals(parentTypeCode)
            || declaration(primaryTypes.getFirst()).mode() != Mode.NONE) return new ResourceAssociationRuntime.Context(primaryId, rules, applicable);
        Set<Long> references = rules.values().stream().anyMatch(rule -> rule.mode() == Mode.SHARED)
            ? Set.copyOf(relations.selectSharedTypeIds(tenantId, primaryId)) : Set.of();
        for (var rule : rules.values()) {
            if (rule.active() && rule.parentTypes().contains(parentTypeCode)
                && (rule.mode() == Mode.EXCLUSIVE || references.contains(rule.id()))) applicable.add(rule.value());
        }
        return new ResourceAssociationRuntime.Context(primaryId, rules, applicable);
    }

    @Override
    public Map<Long, ResourceEntity> runtimeResources(Long tenantId, ResourceAssociationRuntime.Context context, Set<Long> ids) {
        if (ids.isEmpty() || context.applicableTypes().isEmpty()) return Map.of();
        List<ResourceEntity> rows = new ArrayList<>();
        SqlBatches.forEach(new ArrayList<>(ids), batch -> rows.addAll(resources.selectValidByIds(tenantId, Set.copyOf(batch))));
        Set<Long> exclusiveIds = rows.stream().filter(row -> context.applicable(row.getResourceType())
            && context.types().get(row.getResourceType()).mode() == Mode.EXCLUSIVE).map(ResourceEntity::getId).collect(Collectors.toSet());
        Map<Long, Long> ownerIds = findByExclusiveIds(tenantId, exclusiveIds).stream()
            .collect(Collectors.toMap(ResourceAssociation::getExclusiveResourceId, ResourceAssociation::getPrimaryResourceId));
        Map<Long, ResourceEntity> result = new HashMap<>();
        for (ResourceEntity row : rows) {
            if (!Integer.valueOf(1).equals(row.getStatus()) || !context.applicable(row.getResourceType())) continue;
            if (context.types().get(row.getResourceType()).mode() == Mode.EXCLUSIVE
                && (row.getSourceEntityId() == null || !Objects.equals(ownerIds.get(row.getId()), context.primaryId()))) continue;
            result.put(row.getId(), row);
        }
        return Map.copyOf(result);
    }

    @Override
    public List<ResourceEntity> exclusiveScope(Long tenantId, ResourceAssociationRuntime.Context context, Set<Integer> values, int limit) {
        Set<Integer> allowed = values.stream().filter(context::applicable)
            .filter(type -> context.types().get(type).mode() == Mode.EXCLUSIVE).collect(Collectors.toSet());
        return allowed.isEmpty() ? List.of() : resources.selectExclusiveScope(tenantId, context.primaryId(), allowed, limit);
    }

    @Override
    public Map<Long, Set<Long>> relatedAncestorClosures(Long tenantId, ResourceAssociationRuntime.Context context, Set<Long> ids) {
        return relatedClosures(tenantId, context, ids, false);
    }

    @Override
    public Map<Long, Set<Long>> relatedDescendantClosures(Long tenantId, ResourceAssociationRuntime.Context context, Set<Long> ids) {
        return relatedClosures(tenantId, context, ids, true);
    }

    private Map<Long, Set<Long>> relatedClosures(Long tenantId, ResourceAssociationRuntime.Context context, Set<Long> ids, boolean descendants) {
        Set<Integer> exclusive = context.types().values().stream().filter(type -> type.mode() == Mode.EXCLUSIVE)
            .map(ResourceAssociationRuntime.TypeRule::value).collect(Collectors.toSet());
        Map<Long, Set<Long>> result = new HashMap<>();
        SqlBatches.forEach(new ArrayList<>(ids), batch -> {
            for (var row : resources.selectRelatedAncestorClosures(tenantId, context.primaryId(), Set.copyOf(batch), exclusive, descendants)) {
                result.computeIfAbsent(row.getTargetId(), ignored -> new HashSet<>()).add(row.getClosureId());
            }
        });
        return result;
    }

    private ResourceAssociationDeclaration declaration(TypeDefinition type) {
        try { return ResourceAssociationDeclaration.parse(json, type.getExtra()); }
        catch (IllegalArgumentException exception) { throw invalid(exception.getMessage()); }
    }

    @Override
    public void validateGrantBindings(Long tenantId, List<GrantBinding> bindings) {
        if (bindings.isEmpty()) return;
        var rules = runtimeTypes(tenantId, bindings.stream().map(GrantBinding::childType).collect(Collectors.toSet()));
        List<GrantBinding> related = bindings.stream().filter(b -> rules.containsKey(b.childType())).toList();
        if (related.isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        related.forEach(b -> { if (b.parentId() != null) ids.add(b.parentId()); if (b.childId() != null) ids.add(b.childId()); });
        Map<Long, Long> owners = findByExclusiveIds(tenantId, ids).stream().collect(Collectors.toMap(
            ResourceAssociation::getExclusiveResourceId, ResourceAssociation::getPrimaryResourceId));
        ids.addAll(owners.values());
        Map<Long, ResourceEntity> entities = new HashMap<>();
        SqlBatches.forEach(new ArrayList<>(ids), batch -> resources.selectValidByIds(tenantId, Set.copyOf(batch))
            .forEach(row -> entities.put(row.getId(), row)));
        Set<Integer> parentTypes = related.stream().map(GrantBinding::parentType).collect(Collectors.toSet());
        Map<Integer, TypeDefinition> definitions = new HashMap<>();
        SqlBatches.forEach(new ArrayList<>(parentTypes), batch -> types.selectResourceTypesByValues(tenantId, Set.copyOf(batch))
            .forEach(type -> definitions.put(type.getTypeValue(), type)));
        Map<Long, Set<Long>> references = new HashMap<>();
        findByPrimaryIds(tenantId, related.stream().map(GrantBinding::parentId).filter(Objects::nonNull).collect(Collectors.toSet()))
            .stream().filter(link -> link.getSharedTypeId() != null).forEach(link -> references
                .computeIfAbsent(link.getPrimaryResourceId(), ignored -> new HashSet<>()).add(link.getSharedTypeId()));
        for (GrantBinding binding : related) {
            var rule = rules.get(binding.childType());
            var primaryType = definitions.get(binding.parentType());
            boolean valid = rule.active() && primaryType != null && declaration(primaryType).mode() == Mode.NONE
                && rule.parentTypes().contains(primaryType.getTypeCode());
            ResourceEntity primary = binding.parentId() == null ? null : entities.get(binding.parentId());
            if (binding.parentId() != null) valid &= activeSource(primary) && Objects.equals(primary.getResourceType(), binding.parentType());
            if (binding.parentId() != null && rule.mode() == Mode.SHARED)
                valid &= references.getOrDefault(binding.parentId(), Set.of()).contains(rule.id());
            if (binding.childId() != null) {
                ResourceEntity child = entities.get(binding.childId());
                valid &= child != null && Integer.valueOf(1).equals(child.getStatus()) && Objects.equals(child.getResourceType(), binding.childType());
                if (rule.mode() == Mode.EXCLUSIVE) {
                    Long ownerId = owners.get(binding.childId());
                    ResourceEntity owner = ownerId == null ? null : entities.get(ownerId);
                    valid &= activeSource(child) && activeSource(owner) && Objects.equals(owner.getResourceType(), binding.parentType())
                        && (binding.parentId() == null || binding.parentId().equals(ownerId));
                }
            }
            if (!valid) throw new BizException(AccessErrorCode.RESOURCE_ASSOCIATION_REQUIRED.getCode(), "子权限不在主资源有效归属或整类引用范围内");
        }
    }

    private static boolean activeSource(ResourceEntity row) {
        return row != null && Integer.valueOf(1).equals(row.getStatus()) && row.getSourceEntityId() != null;
    }

    private static BizException invalid(String message) { return new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(), message); }
    private static BizException conflict(String message) { return new BizException(AccessErrorCode.RESOURCE_ASSOCIATION_CONFLICT.getCode(), message); }
}
