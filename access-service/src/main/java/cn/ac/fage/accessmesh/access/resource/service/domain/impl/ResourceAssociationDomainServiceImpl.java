package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceAssociation;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceAssociationMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceAssociationDomainService;
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

    public ResourceAssociationDomainServiceImpl(ResourceAssociationMapper relations, ResourceEntityMapper resources,
            TypeDefinitionDomainService types, ServiceConfigDomainService services, ResourceTypeOwnershipGuard ownership,
            TreeWriteLockSupport locks, ObjectMapper json, RoleResourcePermissionDomainService grants) {
        this.relations = relations;
        this.resources = resources;
        this.types = types;
        this.services = services;
        this.ownership = ownership;
        this.locks = locks;
        this.json = json;
        this.grants = grants;
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
    }

    private static EntityKey entityKey(ResourceEntity row) { return new EntityKey(row.getResourceType(), row.getCode(), row.getCodeType()); }
    private static Plan missing(String reason) { return Plan.failure("DEPENDENCY_MISSING", reason); }
    private static Plan identityConflict() { return Plan.failure("NON_RETRYABLE", "RESOURCE_ASSOCIATION_IDENTITY_CONFLICT"); }
    private static Plan invalidPublication() { return Plan.failure("NON_RETRYABLE", "RESOURCE_ASSOCIATION_INVALID"); }

    private ResourceAssociationDeclaration declaration(TypeDefinition type) {
        try { return ResourceAssociationDeclaration.parse(json, type.getExtra()); }
        catch (IllegalArgumentException exception) { throw invalid(exception.getMessage()); }
    }

    private static BizException invalid(String message) { return new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(), message); }
    private static BizException conflict(String message) { return new BizException(AccessErrorCode.RESOURCE_ASSOCIATION_CONFLICT.getCode(), message); }
}
