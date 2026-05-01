package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp.ResourceEntry;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp.ScopeEntry;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    private final AbstractUserMapper abstractUserMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceDependencyMapper resourceDependencyMapper;
    private final UserRoleDomainService userRoleDomainService;
    private final PermissionConflictDomainService permissionConflictDomainService;
    private final PermissionConditionDomainService permissionConditionDomainService;
    private final RolePermissionDomainService rolePermissionDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermCacheDomainService permCacheDomainService;
    private final PermissionVersionDomainService permissionVersionDomainService;

    public PermissionServiceImpl(AbstractUserMapper abstractUserMapper,
                                 ResourceEntityMapper resourceEntityMapper,
                                 ResourceApiMappingMapper apiMappingMapper,
                                 OperationPermissionMapper operationPermissionMapper,
                                 RoleResourcePermissionMapper rolePermMapper,
                                 ResourceDependencyMapper resourceDependencyMapper,
                                 UserRoleDomainService userRoleDomainService,
                                 PermissionConflictDomainService permissionConflictDomainService,
                                 PermissionConditionDomainService permissionConditionDomainService,
                                 RolePermissionDomainService rolePermissionDomainService,
                                 TypeResolutionService typeResolutionService,
                                 PermCacheDomainService permCacheDomainService,
                                 PermissionVersionDomainService permissionVersionDomainService) {
        this.abstractUserMapper = abstractUserMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.rolePermMapper = rolePermMapper;
        this.resourceDependencyMapper = resourceDependencyMapper;
        this.userRoleDomainService = userRoleDomainService;
        this.permissionConflictDomainService = permissionConflictDomainService;
        this.permissionConditionDomainService = permissionConditionDomainService;
        this.rolePermissionDomainService = rolePermissionDomainService;
        this.typeResolutionService = typeResolutionService;
        this.permCacheDomainService = permCacheDomainService;
        this.permissionVersionDomainService = permissionVersionDomainService;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return AuthCheckResp.deny("USER_NOT_FOUND");
        }
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Long resourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.domainCode());
        if (resourceEntityId == null) {
            return AuthCheckResp.deny("RESOURCE_NOT_FOUND");
        }
        Long operationPermissionId = typeResolutionService.resolveOperationId(
            tenantId, req.operationCode(), req.resourceTypeCode());
        if (operationPermissionId == null) {
            return AuthCheckResp.deny("OPERATION_NOT_FOUND");
        }
        return checkInternal(tenantId, userId, resourceEntityId, operationPermissionId,
            bizDomainId, req.inheritMode(), req.context());
    }

    @Override
    @Transactional(readOnly = true)
    public BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            List<AuthCheckItemResult> results = req.items().stream()
                .map(item -> new AuthCheckItemResult(
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND",
                    List.of(), List.of()))
                .collect(Collectors.toList());
            return new BatchAuthCheckResp(results);
        }

        List<AuthCheckItemResult> results = new ArrayList<>();
        Map<String, Object> context = req.context() != null ? req.context() : Map.of();

        for (BatchAuthCheckReq.AuthCheckItem item : req.items()) {
            Long domainId = typeResolutionService.resolveDomainId(tenantId, item.domainCode());
            Long resourceEntityId = typeResolutionService.resolveResourceId(
                tenantId, item.resourceTypeCode(), item.resourceCode(), item.codeType(), item.domainCode());
            Long operationId = (resourceEntityId != null)
                ? typeResolutionService.resolveOperationId(tenantId, item.operationCode(), item.resourceTypeCode())
                : null;

            AuthCheckResp resp;
            if (resourceEntityId == null) {
                resp = AuthCheckResp.deny("RESOURCE_NOT_FOUND");
            } else if (operationId == null) {
                resp = AuthCheckResp.deny("OPERATION_NOT_FOUND");
            } else {
                resp = checkInternal(tenantId, userId, resourceEntityId, operationId, domainId, item.inheritMode(), context);
            }
            results.add(new AuthCheckItemResult(
                item.resourceTypeCode(), item.resourceCode(), item.operationCode(),
                resp.allowed(), resp.reason(), resp.matchedRoleIds(), resp.matchedPermissionIds()));
        }
        return new BatchAuthCheckResp(results);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return CheckInterfaceResp.deny("USER_NOT_FOUND");
        }
        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user == null || user.getDeleteFlag() != 0L) {
            return CheckInterfaceResp.deny("USER_NOT_FOUND");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return CheckInterfaceResp.deny("USER_DISABLED");
        }

        List<ResourceApiMapping> mappings = apiMappingMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(RESOURCE_API_MAPPING.SERVICE_CODE.eq(req.serviceCode()))
                .and(RESOURCE_API_MAPPING.HTTP_METHOD.eq(req.httpMethod()))
                .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
                .and(RESOURCE_API_MAPPING.ENABLED.eq(true))
        );

        if (mappings.isEmpty()) {
            return CheckInterfaceResp.deny("API_NOT_REGISTERED");
        }

        List<ResourceApiMapping> matchedMappings = mappings.stream()
            .filter(mapping -> pathMatches(mapping.getPathPattern(), req.path()))
            .toList();
        if (matchedMappings.isEmpty()) {
            return CheckInterfaceResp.deny("API_NOT_REGISTERED");
        }

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, null);
        if (effectiveRoleIds.isEmpty()) {
            return CheckInterfaceResp.deny("NO_ROLE");
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return CheckInterfaceResp.deny("NO_ROLE");
        }

        Map<String, Object> context = req.context() != null ? req.context() : Map.of();
        List<CheckInterfaceResp.MatchedResource> matchedResources = new ArrayList<>();
        boolean anyAllowed = false;
        boolean hasMatchedResource = false;
        Map<Integer, List<OperationPermission>> operationCacheByType = new HashMap<>();

        for (ResourceApiMapping matchedMapping : matchedMappings) {
            ResourceEntity resource = resourceEntityMapper.selectOneById(matchedMapping.getResourceEntityId());
            if (resource == null || resource.getDeleteFlag() != 0L) {
                continue;
            }
            hasMatchedResource = true;

            List<OperationPermission> allOps = operationCacheByType.computeIfAbsent(resource.getResourceType(), rt ->
                operationPermissionMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                        .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(rt))
                        .and(OPERATION_PERMISSION.CODE.eq("ACCESS"))
                        .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
                )
            );
            String resourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", resource.getResourceType());
            for (OperationPermission op : allOps) {
                List<RolePermEntry> entries = queryMatchedEntries(tenantId, validRoleIds,
                    matchedMapping.getResourceEntityId(), op.getId(), null);
                List<RolePermEntry> passed = entries.isEmpty()
                    ? List.of()
                    : permissionConditionDomainService.evaluate(tenantId, entries, context);
                List<RolePermEntry> finalEntries = passed.isEmpty()
                    ? List.of()
                    : permissionConflictDomainService.filterPermMutex(tenantId, passed);
                boolean allowed = !finalEntries.isEmpty();
                if (allowed) {
                    anyAllowed = true;
                }
                List<Long> matchedRoleIds = finalEntries.stream()
                    .map(RolePermEntry::roleId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
                List<Long> matchedPermissionIds = finalEntries.stream()
                    .map(RolePermEntry::permissionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
                matchedResources.add(new CheckInterfaceResp.MatchedResource(
                    resource.getId(),
                    resourceTypeCode,
                    resource.getCode(),
                    op.getCode(),
                    allowed,
                    matchedRoleIds,
                    matchedPermissionIds
                ));
            }
        }

        if (!hasMatchedResource) {
            return CheckInterfaceResp.deny("RESOURCE_NOT_FOUND", List.of(), 30);
        }
        if (anyAllowed) {
            return CheckInterfaceResp.allow(matchedResources, 30);
        }
        return CheckInterfaceResp.deny("NO_PERMISSION", matchedResources, 30);
    }

    @Override
    @Transactional(readOnly = true)
    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), "", 60);

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        List<String> resourceTypeCodes = req.resourceTypeCodes() == null ? List.of() : req.resourceTypeCodes();
        if (resourceTypeCodes.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);
        List<String> operationCodes = req.operationCodes() == null ? List.of() : req.operationCodes();
        if (operationCodes.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);

        Set<Integer> resourceTypes = resourceTypeCodes.stream()
            .map(code -> typeResolutionService.resolveTypeValue(tenantId, "resource_type", code))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (resourceTypes.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);

        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
            .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.in(resourceTypes))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(qw);
        boolean includeInherited = req.includeInherited() == null || req.includeInherited();
        if (!includeInherited) {
            perms = perms.stream()
                .filter(p -> "MANUAL".equalsIgnoreCase(p.getGrantSource()))
                .toList();
        }

        Map<Long, List<RoleResourcePermission>> permsByResource = perms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));
        List<RoleResourcePermission> scopeAllPerms = perms.stream()
            .filter(p -> Boolean.TRUE.equals(p.getScopeAll()) || p.getResourceEntityId() == null)
            .toList();

        List<ResourceEntry> items = new ArrayList<>();
        boolean includeChildren = req.includeChildren() == null || req.includeChildren();
        String requiredCodeType = req.codeType();
        String resourceCodePrefix = req.context() != null ? Objects.toString(req.context().get("resourceCodePrefix"), null) : null;
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : permsByResource.entrySet()) {
            Long rid = entry.getKey();
            List<RoleResourcePermission> resourcePerms = entry.getValue();
            ResourceEntity r = resourceEntityMapper.selectOneById(rid);
            if (r == null || r.getDeleteFlag() != 0L) continue;
            if (requiredCodeType != null && !requiredCodeType.isBlank() && !requiredCodeType.equals(r.getCodeType())) {
                continue;
            }
            if (!includeChildren && r.getParentId() != null) {
                continue;
            }
            if (resourceCodePrefix != null && !resourceCodePrefix.isBlank()
                && (r.getCode() == null || !r.getCode().startsWith(resourceCodePrefix))) {
                continue;
            }

            boolean canManage = resourcePerms.stream().anyMatch(p -> Boolean.TRUE.equals(p.getCanManage()));
            List<String> operations = resourcePerms.stream()
                .map(p -> {
                    OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
                    return op != null ? op.getCode() : null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            List<Long> matchedRoleIds = resourcePerms.stream()
                .map(RoleResourcePermission::getAbstractRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            List<Long> matchedPermissionIds = resourcePerms.stream()
                .map(RoleResourcePermission::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            List<String> grantSources = resourcePerms.stream()
                .map(RoleResourcePermission::getGrantSource)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

            List<String> filteredOperations = operations.stream()
                .filter(operationCodes::contains)
                .toList();
            if (filteredOperations.isEmpty()) {
                continue;
            }
            String resolvedResourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", r.getResourceType());
            items.add(new ResourceEntry(
                resolvedResourceTypeCode, r.getCode(), r.getCodeType(), r.getName(),
                canManage, filteredOperations, matchedRoleIds, matchedPermissionIds, grantSources
            ));
        }
        if (!scopeAllPerms.isEmpty()) {
            Map<Integer, List<RoleResourcePermission>> scopeAllByType = scopeAllPerms.stream()
                .filter(p -> p.getResourceType() != null)
                .collect(Collectors.groupingBy(RoleResourcePermission::getResourceType));
            for (Map.Entry<Integer, List<RoleResourcePermission>> scopeEntry : scopeAllByType.entrySet()) {
                Integer resourceType = scopeEntry.getKey();
                String resolvedResourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", resourceType);
                List<String> allowedOps = scopeEntry.getValue().stream()
                    .map(p -> operationPermissionMapper.selectOneById(p.getOperationPermissionId()))
                    .filter(Objects::nonNull)
                    .map(OperationPermission::getCode)
                    .filter(operationCodes::contains)
                    .distinct()
                    .toList();
                if (allowedOps.isEmpty()) {
                    continue;
                }
                List<ResourceEntity> allTypeResources = resourceEntityMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                        .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                        .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                );
                for (ResourceEntity r : allTypeResources) {
                    if (requiredCodeType != null && !requiredCodeType.isBlank() && !requiredCodeType.equals(r.getCodeType())) {
                        continue;
                    }
                    if (!includeChildren && r.getParentId() != null) {
                        continue;
                    }
                    if (resourceCodePrefix != null && !resourceCodePrefix.isBlank()
                        && (r.getCode() == null || !r.getCode().startsWith(resourceCodePrefix))) {
                        continue;
                    }
                    if (items.stream().anyMatch(item -> Objects.equals(item.resourceCode(), r.getCode())
                        && Objects.equals(item.resourceTypeCode(), resolvedResourceTypeCode))) {
                        continue;
                    }
                    List<Long> matchedRoleIds = scopeEntry.getValue().stream()
                        .map(RoleResourcePermission::getAbstractRoleId).filter(Objects::nonNull).distinct().toList();
                    List<Long> matchedPermissionIds = scopeEntry.getValue().stream()
                        .map(RoleResourcePermission::getId).filter(Objects::nonNull).distinct().toList();
                    List<String> grantSources = scopeEntry.getValue().stream()
                        .map(RoleResourcePermission::getGrantSource).filter(Objects::nonNull).distinct().toList();
                    boolean canManage = scopeEntry.getValue().stream().anyMatch(p -> Boolean.TRUE.equals(p.getCanManage()));
                    items.add(new ResourceEntry(
                        resolvedResourceTypeCode, r.getCode(), r.getCodeType(), r.getName(),
                        canManage, allowedOps, matchedRoleIds, matchedPermissionIds, grantSources
                    ));
                }
            }
        }
        if (Boolean.TRUE.equals(req.treeMode())) {
            items = items.stream()
                .sorted(Comparator.comparing(ResourceEntry::resourceCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        }

        long version = validRoleIds.stream()
            .mapToLong(roleId -> permissionVersionDomainService.getCurrentVersion(tenantId, roleId))
            .max()
            .orElse(0L);
        String permissionVersion = userId + ":" + version;
        return new QueryResourcesResp(items, permissionVersion, 60);
    }

    @Override
    @Transactional(readOnly = true)
    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryScopesResp(false, "USER_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);

        Long parentResourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.parentResourceTypeCode(), req.parentResourceCode(), req.parentCodeType(), req.domainCode());
        if (parentResourceEntityId == null) {
            return new QueryScopesResp(false, "OBJECT_KEY_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) return new QueryScopesResp(false, "NO_ROLE", List.of(), List.of(), List.of(), "UNION", "", 60);
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new QueryScopesResp(false, "NO_ROLE", List.of(), List.of(), List.of(), "UNION", "", 60);

        Map<String, Object> context = req.context() != null ? req.context() : Map.of();
        List<RolePermEntry> parentEntries = new ArrayList<>();
        Set<String> matchedParentOps = new HashSet<>();
        for (String parentOpCode : req.parentOperationCodes()) {
            Long parentOpId = typeResolutionService.resolveOperationId(tenantId, parentOpCode, req.parentResourceTypeCode());
            if (parentOpId == null) {
                continue;
            }
            List<RolePermEntry> oneOpEntries = queryMatchedEntries(
                tenantId, validRoleIds, parentResourceEntityId, parentOpId, null
            );
            oneOpEntries = permissionConditionDomainService.evaluate(tenantId, oneOpEntries, context);
            oneOpEntries = permissionConflictDomainService.filterPermMutex(tenantId, oneOpEntries);
            if (!oneOpEntries.isEmpty()) {
                matchedParentOps.add(parentOpCode);
                parentEntries.addAll(oneOpEntries);
            }
        }
        Set<Long> parentPermissionIds = parentEntries.stream()
            .map(RolePermEntry::permissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (parentPermissionIds.isEmpty()) {
            return new QueryScopesResp(false, "NO_PERMISSION", List.of(), List.of(), List.of(), "UNION", "", 60);
        }
        Map<String, ScopeAccumulator> merged = new LinkedHashMap<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            Integer scopeType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", scopeTypeCode);
            if (scopeType == null) {
                continue;
            }
            List<RoleResourcePermission> scopePermCandidates = rolePermMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
                    .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(scopeType))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            for (String scopeOpCode : req.scopeOperationCodes()) {
                Long scopeOpId = typeResolutionService.resolveOperationId(tenantId, scopeOpCode, scopeTypeCode);
                if (scopeOpId == null) {
                    continue;
                }
                List<RolePermEntry> entries = toRolePermEntries(scopePermCandidates).stream()
                    .filter(entry -> entry.operationPermissionId().equals(scopeOpId))
                    .filter(entry -> entry.dependOn() == null || parentPermissionIds.contains(entry.dependOn()))
                    .toList();
                entries = permissionConditionDomainService.evaluate(tenantId, entries, context);
                entries = permissionConflictDomainService.filterPermMutex(tenantId, entries);

                for (RolePermEntry entry : entries) {
                    boolean scopeAll = entry.resourceEntityId() == null;
                    ResourceEntity resource = scopeAll ? null : resourceEntityMapper.selectOneById(entry.resourceEntityId());
                    if (!scopeAll && (resource == null || resource.getDeleteFlag() != 0L)) {
                        continue;
                    }
                    String source = entry.dependOn() == null ? "DIRECT" : "DEPENDENT";
                    String mergeKey = scopeAll
                        ? "ALL|" + scopeTypeCode
                        : "ONE|" + scopeTypeCode + "|" + resource.getCodeType() + "|" + resource.getCode();
                    ScopeAccumulator accumulator = merged.computeIfAbsent(
                        mergeKey,
                        key -> new ScopeAccumulator(
                            scopeTypeCode,
                            scopeAll ? null : resource.getCode(),
                            scopeAll ? null : resource.getCodeType(),
                            scopeAll ? null : resource.getName(),
                            scopeAll
                        )
                    );
                    accumulator.operations.add(scopeOpCode);
                    accumulator.sources.add(source);
                    if (entry.roleId() != null) {
                        accumulator.matchedRoleIds.add(entry.roleId());
                    }
                    if (entry.permissionId() != null) {
                        accumulator.matchedPermissionIds.add(entry.permissionId());
                    }
                    if (entry.dependOn() != null) {
                        accumulator.dependOnPermissionIds.add(entry.dependOn());
                    }
                }
            }
        }
        List<ScopeEntry> items = merged.values().stream()
            .map(item -> new ScopeEntry(
                item.resourceTypeCode,
                item.resourceCode,
                item.codeType,
                item.resourceName,
                item.scopeAll,
                new ArrayList<>(item.operations),
                new ArrayList<>(item.sources),
                new ArrayList<>(item.matchedRoleIds),
                new ArrayList<>(item.matchedPermissionIds),
                new ArrayList<>(item.dependOnPermissionIds)
            ))
            .toList();
        long version = validRoleIds.stream()
            .mapToLong(roleId -> permissionVersionDomainService.getCurrentVersion(tenantId, roleId))
            .max()
            .orElse(0L);
        String permissionVersion = userId + ":" + version;
        return new QueryScopesResp(
            true,
            null,
            new ArrayList<>(matchedParentOps),
            new ArrayList<>(parentPermissionIds),
            items,
            "UNION",
            permissionVersion,
            60
        );
    }

    private List<RolePermEntry> toRolePermEntries(List<RoleResourcePermission> perms) {
        if (perms.isEmpty()) {
            return List.of();
        }
        Map<Long, OperationPermission> opCache = new HashMap<>();
        List<RolePermEntry> entries = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = opCache.computeIfAbsent(
                perm.getOperationPermissionId(), operationPermissionMapper::selectOneById);
            if (grantedOp == null) {
                continue;
            }
            entries.add(new RolePermEntry(
                perm.getId(),
                perm.getAbstractRoleId(),
                perm.getResourceEntityId(),
                null,
                perm.getResourceType(),
                perm.getOperationPermissionId(),
                grantedOp.getCode(),
                null,
                perm.getGrantSource(),
                perm.getCanManage(),
                perm.getConditionId(),
                perm.getConditionId() != null,
                perm.getDependOn()
            ));
        }
        return entries;
    }

    @Override
    @Transactional(readOnly = true)
    public InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new InterfaceSnapshotResp(false, 0, List.of());

        var cached = permCacheDomainService.getInterfaceSnapshot(tenantId, req.serviceCode());
        if (cached.isPresent()) {
            long cachedVersion = cached.get().version();
            if (req.permissionVersion() != null && req.permissionVersion().equals(cachedVersion)) {
                return new InterfaceSnapshotResp(true, cachedVersion, List.of());
            }
            List<ApiPermissionEntry> entries = cached.get().entries().stream()
                .map(e -> new ApiPermissionEntry(e.serviceCode(), e.httpMethod(), e.pathPattern(),
                    e.hasCondition(), e.conditionId()))
                .collect(Collectors.toList());
            return new InterfaceSnapshotResp(false, cachedVersion, entries);
        }

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, null);
        if (effectiveRoleIds.isEmpty()) return new InterfaceSnapshotResp(false, 0, List.of());
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new InterfaceSnapshotResp(false, 0, List.of());
        long currentVersion = validRoleIds.stream()
            .mapToLong(roleId -> permissionVersionDomainService.getCurrentVersion(tenantId, roleId))
            .max()
            .orElse(0L);
        if (req.permissionVersion() != null && req.permissionVersion().equals(currentVersion)) {
            return new InterfaceSnapshotResp(true, currentVersion, List.of());
        }

        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Set<Long> allowedResourceIds = allPerms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<ApiPermissionEntry> entries = new ArrayList<>();
        if (!allowedResourceIds.isEmpty()) {
            List<ResourceApiMapping> apiMappings = apiMappingMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_API_MAPPING.SERVICE_CODE.eq(req.serviceCode()))
                    .and(RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.in(allowedResourceIds))
                    .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
                    .and(RESOURCE_API_MAPPING.ENABLED.eq(true))
            );
            for (ResourceApiMapping mapping : apiMappings) {
                boolean hasCondition = allPerms.stream()
                    .anyMatch(p -> p.getResourceEntityId().equals(mapping.getResourceEntityId()) && p.getConditionId() != null);
                Long conditionId = allPerms.stream()
                    .filter(p -> p.getResourceEntityId().equals(mapping.getResourceEntityId()) && p.getConditionId() != null)
                    .map(RoleResourcePermission::getConditionId).findFirst().orElse(null);
                entries.add(new ApiPermissionEntry(mapping.getServiceCode(), mapping.getHttpMethod(),
                    mapping.getPathPattern(), hasCondition, conditionId));
            }
        }
        List<ApiPermissionEntry> dedupedEntries = entries.stream()
            .collect(Collectors.toMap(
                item -> item.serviceCode() + "|" + item.httpMethod() + "|" + item.pathPattern(),
                item -> item,
                (left, right) -> left.hasCondition() ? left : right,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
        InterfaceSnapshot snapshot = new InterfaceSnapshot(
            tenantId,
            req.serviceCode(),
            currentVersion,
            dedupedEntries.stream()
                .map(item -> new InterfaceSnapshot.InterfacePermEntry(
                    item.serviceCode(), item.httpMethod(), item.pathPattern(), item.hasCondition(), item.conditionId()
                ))
                .toList()
        );
        permCacheDomainService.setInterfaceSnapshot(tenantId, req.serviceCode(), snapshot);
        return new InterfaceSnapshotResp(false, currentVersion, dedupedEntries);
    }

    // =========== Internal helpers ===========

    private AuthCheckResp checkInternal(Long tenantId, Long userId, Long resourceEntityId,
                                        Long operationPermissionId, Long bizDomainId,
                                        String inheritMode, Map<String, Object> context) {
        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user == null || user.getDeleteFlag() != 0L) return AuthCheckResp.deny("USER_NOT_FOUND");
        if (!Boolean.TRUE.equals(user.getEnabled())) return AuthCheckResp.deny("USER_DISABLED");

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) return AuthCheckResp.deny("NO_ROLE");

        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return AuthCheckResp.deny("NO_ROLE");

        List<RolePermEntry> entries = queryMatchedEntries(tenantId, validRoleIds, resourceEntityId,
            operationPermissionId, inheritMode);
        if (entries.isEmpty()) return AuthCheckResp.deny("NO_PERMISSION");

        Map<String, Object> ctx = context != null ? context : Map.of();
        List<RolePermEntry> passedEntries = permissionConditionDomainService.evaluate(tenantId, entries, ctx);
        if (passedEntries.isEmpty()) return AuthCheckResp.deny("CONDITION_NOT_MET");

        List<RolePermEntry> finalEntries = permissionConflictDomainService.filterPermMutex(tenantId, passedEntries);
        if (finalEntries.isEmpty()) return AuthCheckResp.deny("CONFLICT_DETECTED");

        boolean conditionEvaluated = passedEntries.stream().anyMatch(RolePermEntry::hasCondition);
        List<Long> matchedRoleIds = finalEntries.stream().map(RolePermEntry::roleId).filter(Objects::nonNull).distinct().toList();
        List<Long> matchedPermissionIds = finalEntries.stream().map(RolePermEntry::permissionId).filter(Objects::nonNull).distinct().toList();
        return AuthCheckResp.allow(matchedRoleIds, matchedPermissionIds, conditionEvaluated);
    }

    private List<RolePermEntry> queryMatchedEntries(Long tenantId, Set<Long> roleIds,
                                                    Long resourceEntityId, Long operationPermissionId,
                                                    String inheritMode) {
        Set<Long> targetResourceIds = new HashSet<>();
        targetResourceIds.add(resourceEntityId);
        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
            .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));

        if ("PARENT".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> parentIds = getAncestorIds(tenantId, resourceEntityId);
            if (!parentIds.isEmpty()) {
                targetResourceIds.addAll(parentIds);
            }
        }
        if ("CHILDREN".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> childIds = getDescendantIds(tenantId, resourceEntityId);
            if (!childIds.isEmpty()) {
                targetResourceIds.addAll(childIds);
            }
        }
        if (targetResourceIds.size() > 1) {
            qw = QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(targetResourceIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(qw);
        OperationPermission targetOp = operationPermissionMapper.selectOneById(operationPermissionId);
        if (targetOp == null) return Collections.emptyList();

        Map<Long, OperationPermission> opCache = new HashMap<>();
        opCache.put(operationPermissionId, targetOp);

        List<RolePermEntry> result = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = opCache.computeIfAbsent(
                perm.getOperationPermissionId(), operationPermissionMapper::selectOneById);
            if (grantedOp == null) continue;

            long effectiveBits = (grantedOp.getBinaryBit() != null ? grantedOp.getBinaryBit() : 0L)
                | (grantedOp.getInheritMask() != null ? grantedOp.getInheritMask() : 0L);
            long targetBit = targetOp.getBinaryBit() != null ? targetOp.getBinaryBit() : 0L;
            if (targetBit != 0L && (effectiveBits & targetBit) != 0) {
                result.add(new RolePermEntry(
                    perm.getId(), perm.getAbstractRoleId(),
                    perm.getResourceEntityId(), null, perm.getResourceType(),
                    perm.getOperationPermissionId(), grantedOp.getCode(), null,
                    perm.getGrantSource(),
                    perm.getCanManage(), perm.getConditionId(), perm.getConditionId() != null,
                    perm.getDependOn()));
            }
        }
        return result;
    }

    private static final class ScopeAccumulator {
        private final String resourceTypeCode;
        private final String resourceCode;
        private final String codeType;
        private final String resourceName;
        private final boolean scopeAll;
        private final Set<String> operations = new LinkedHashSet<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private final Set<Long> matchedRoleIds = new LinkedHashSet<>();
        private final Set<Long> matchedPermissionIds = new LinkedHashSet<>();
        private final Set<Long> dependOnPermissionIds = new LinkedHashSet<>();

        private ScopeAccumulator(
            String resourceTypeCode,
            String resourceCode,
            String codeType,
            String resourceName,
            boolean scopeAll
        ) {
            this.resourceTypeCode = resourceTypeCode;
            this.resourceCode = resourceCode;
            this.codeType = codeType;
            this.resourceName = resourceName;
            this.scopeAll = scopeAll;
        }
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        if (pattern.contains("{")) {
            String[] pp = pattern.split("/"), ap = path.split("/");
            if (pp.length != ap.length) return false;
            for (int i = 0; i < pp.length; i++) {
                if (pp[i].startsWith("{") && pp[i].endsWith("}")) continue;
                if (!pp[i].equals(ap[i])) return false;
            }
            return true;
        }
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("**", ".*").replace("*", "[^/]*");
            return path.matches(regex);
        }
        return false;
    }

    private List<Long> getAncestorIds(Long tenantId, Long resourceEntityId) {
        List<Long> ids = new ArrayList<>();
        Long current = resourceEntityId;
        while (current != null) {
            ResourceEntity e = resourceEntityMapper.selectOneById(current);
            if (e == null || e.getDeleteFlag() != 0L || !e.getTenantId().equals(tenantId)) break;
            if (e.getParentId() != null) { ids.add(e.getParentId()); current = e.getParentId(); } else break;
        }
        return ids;
    }

    private List<Long> getDescendantIds(Long tenantId, Long resourceEntityId) {
        List<Long> ids = new ArrayList<>();
        collectDescendants(tenantId, resourceEntityId, ids);
        return ids;
    }

    private void collectDescendants(Long tenantId, Long parentId, List<Long> result) {
        List<ResourceEntity> children = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .where(RESOURCE_ENTITY.PARENT_ID.eq(parentId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        for (ResourceEntity child : children) {
            result.add(child.getId());
            collectDescendants(tenantId, child.getId(), result);
        }
    }
}
