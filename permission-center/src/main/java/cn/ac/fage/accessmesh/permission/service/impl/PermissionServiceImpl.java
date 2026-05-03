package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionTreeReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp.TreeNode;
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
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
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
import org.springframework.util.AntPathMatcher;

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

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final EntityBatchLoadDomainService entityBatchLoadDomainService;

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
                                 PermissionVersionDomainService permissionVersionDomainService,
                                 ResourceEntityDomainService resourceEntityDomainService,
                                 EntityBatchLoadDomainService entityBatchLoadDomainService) {
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
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.entityBatchLoadDomainService = entityBatchLoadDomainService;
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

        // ===== Batch resolution to avoid N+1 queries =====
        // 1. Collect all unique domain codes
        Set<String> domainCodes = req.items().stream()
            .map(BatchAuthCheckReq.AuthCheckItem::domainCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Long> domainIdMap = typeResolutionService.batchResolveDomainIds(tenantId, domainCodes);

        // 2. Collect all unique resource requests
        List<ResourceResolveRequest> resourceRequests = req.items().stream()
            .map(item -> new ResourceResolveRequest(
                item.resourceTypeCode(),
                item.resourceCode(),
                item.codeType(),
                item.domainCode()))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        // 3. Collect all unique operation codes by resource type
        Map<String, Set<String>> operationCodesByType = req.items().stream()
            .collect(Collectors.groupingBy(
                BatchAuthCheckReq.AuthCheckItem::resourceTypeCode,
                Collectors.mapping(BatchAuthCheckReq.AuthCheckItem::operationCode, Collectors.toSet())
            ));
        Map<String, Map<String, Long>> operationIdMapByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : operationCodesByType.entrySet()) {
            Map<String, Long> opMap = typeResolutionService.batchResolveOperationIds(tenantId, entry.getKey(), entry.getValue());
            operationIdMapByType.put(entry.getKey(), opMap);
        }

        List<AuthCheckItemResult> results = new ArrayList<>();
        Map<String, Object> context = req.context() != null ? req.context() : Map.of();

        for (BatchAuthCheckReq.AuthCheckItem item : req.items()) {
            Long domainId = item.domainCode() != null ? domainIdMap.get(item.domainCode()) : null;
            Long resourceEntityId = resourceIdMap.get(new ResourceResolveKey(
                item.resourceTypeCode(), item.resourceCode(), item.codeType(), item.domainCode()));
            Map<String, Long> opMap = operationIdMapByType.getOrDefault(item.resourceTypeCode(), Map.of());
            Long operationId = opMap.get(item.operationCode());

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
        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.ID.eq(userId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        if (user == null) {
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

        // Batch load resource entities for matched mappings to avoid N+1 queries
        Set<Long> mappingResourceIds = matchedMappings.stream()
            .map(ResourceApiMapping::getResourceEntityId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, ResourceEntity> mappingResourceMap = entityBatchLoadDomainService.batchLoadResources(tenantId, mappingResourceIds);

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
            ResourceEntity resource = mappingResourceMap.get(matchedMapping.getResourceEntityId());
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
        // 1. 参数验证
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), "", 60);

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        List<String> resourceTypeCodes = req.resourceTypeCodes() == null ? List.of() : req.resourceTypeCodes();
        if (resourceTypeCodes.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);
        List<String> operationCodes = req.operationCodes() == null ? List.of() : req.operationCodes();
        if (operationCodes.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);

        // Batch resolve resource type codes to values (avoid N+1)
        Set<String> typeCodeSet = new HashSet<>(resourceTypeCodes);
        Map<String, Integer> resourceTypeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", typeCodeSet);
        Set<Integer> resourceTypes = resourceTypeValueMap.values().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (resourceTypes.isEmpty()) return new QueryResourcesResp(List.of(), "", 60);

        // 2. 准备上下文
        QueryResourcesContext context = prepareQueryResourcesContext(tenantId, userId, bizDomainId, resourceTypes, req);

        // 3. 查询权限
        List<RoleResourcePermission> perms = queryRoleResourcePermissions(tenantId, context.validRoleIds, resourceTypes, context.includeInherited);

        // 4. 处理资源权限
        List<ResourceEntry> items = processResourcePermissions(tenantId, perms, context, operationCodes);

        // 5. 处理scopeAll权限
        processScopeAllPermissions(tenantId, perms, items, context, operationCodes);

        // 6. 排序返回
        return buildQueryResourcesResponse(items, userId, tenantId, context.validRoleIds, req);
    }

    /**
     * 查询资源权限上下文
     */
    private static class QueryResourcesContext {
        final Long userId;
        final Long bizDomainId;
        final Set<Integer> resourceTypes;
        final Set<Long> validRoleIds;
        final boolean includeInherited;
        final boolean includeChildren;
        final String requiredCodeType;
        final String resourceCodePrefix;

        QueryResourcesContext(Long userId, Long bizDomainId, Set<Integer> resourceTypes,
                              Set<Long> validRoleIds, boolean includeInherited, boolean includeChildren,
                              String requiredCodeType, String resourceCodePrefix) {
            this.userId = userId;
            this.bizDomainId = bizDomainId;
            this.resourceTypes = resourceTypes;
            this.validRoleIds = validRoleIds;
            this.includeInherited = includeInherited;
            this.includeChildren = includeChildren;
            this.requiredCodeType = requiredCodeType;
            this.resourceCodePrefix = resourceCodePrefix;
        }
    }

    private QueryResourcesContext prepareQueryResourcesContext(Long tenantId, Long userId, Long bizDomainId,
                                                                Set<Integer> resourceTypes, QueryResourcesReq req) {
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) {
            return new QueryResourcesContext(userId, bizDomainId, resourceTypes, Set.of(),
                true, true, null, null);
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return new QueryResourcesContext(userId, bizDomainId, resourceTypes, Set.of(),
                true, true, null, null);
        }

        boolean includeInherited = req.includeInherited() == null || req.includeInherited();
        boolean includeChildren = req.includeChildren() == null || req.includeChildren();
        String requiredCodeType = req.codeType();
        String resourceCodePrefix = req.context() != null ? Objects.toString(req.context().get("resourceCodePrefix"), null) : null;

        return new QueryResourcesContext(userId, bizDomainId, resourceTypes, validRoleIds,
            includeInherited, includeChildren, requiredCodeType, resourceCodePrefix);
    }

    private List<RoleResourcePermission> queryRoleResourcePermissions(Long tenantId, Set<Long> validRoleIds,
                                                                        Set<Integer> resourceTypes, boolean includeInherited) {
        if (validRoleIds.isEmpty()) {
            return List.of();
        }

        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
            .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.in(resourceTypes))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(qw);
        if (!includeInherited) {
            perms = perms.stream()
                .filter(p -> "MANUAL".equalsIgnoreCase(p.getGrantSource()))
                .toList();
        }
        return perms;
    }

    private List<ResourceEntry> processResourcePermissions(Long tenantId, List<RoleResourcePermission> perms,
                                                             QueryResourcesContext context, List<String> operationCodes) {
        Map<Long, List<RoleResourcePermission>> permsByResource = perms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));

        // Batch load resource entities to avoid N+1 queries
        Set<Long> resourceIds = permsByResource.keySet();
        Map<Long, ResourceEntity> resourceMap = entityBatchLoadDomainService.batchLoadResources(tenantId, resourceIds);

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> operationIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> operationMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);

        // Batch resolve resource type codes (avoid N+1)
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        resourceTypeValues.addAll(resourceMap.values().stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        List<ResourceEntry> items = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : permsByResource.entrySet()) {
            Long rid = entry.getKey();
            List<RoleResourcePermission> resourcePerms = entry.getValue();
            ResourceEntity r = resourceMap.get(rid);
            if (r == null || r.getDeleteFlag() != 0L) continue;
            if (context.requiredCodeType != null && !context.requiredCodeType.isBlank() && !context.requiredCodeType.equals(r.getCodeType())) {
                continue;
            }
            if (!context.includeChildren && r.getParentId() != null) {
                continue;
            }
            if (context.resourceCodePrefix != null && !context.resourceCodePrefix.isBlank()
                && (r.getCode() == null || !r.getCode().startsWith(context.resourceCodePrefix))) {
                continue;
            }

            ResourceEntry resourceEntry = buildResourceEntry(tenantId, r, resourcePerms, operationMap, operationCodes, resourceTypeCodeMap);
            if (resourceEntry != null) {
                items.add(resourceEntry);
            }
        }
        return items;
    }

    private ResourceEntry buildResourceEntry(Long tenantId, ResourceEntity r,
                                              List<RoleResourcePermission> resourcePerms,
                                              Map<Long, OperationPermission> operationMap,
                                              List<String> operationCodes,
                                              Map<Integer, String> resourceTypeCodeMap) {
        boolean canGrant = resourcePerms.stream().anyMatch(p -> Boolean.TRUE.equals(p.getCanGrant()));
        List<String> operations = resourcePerms.stream()
            .map(p -> {
                OperationPermission op = operationMap.get(p.getOperationPermissionId());
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
            return null;
        }
        String resolvedResourceTypeCode = resourceTypeCodeMap.get(r.getResourceType());
        return new ResourceEntry(
            resolvedResourceTypeCode, r.getCode(), r.getCodeType(), r.getName(),
            canGrant, filteredOperations, matchedRoleIds, matchedPermissionIds, grantSources
        );
    }

    private void processScopeAllPermissions(Long tenantId, List<RoleResourcePermission> perms,
                                             List<ResourceEntry> items, QueryResourcesContext context,
                                             List<String> operationCodes) {
        List<RoleResourcePermission> scopeAllPerms = perms.stream()
            .filter(p -> Boolean.TRUE.equals(p.getScopeAll()) || p.getResourceEntityId() == null)
            .toList();

        if (scopeAllPerms.isEmpty()) {
            return;
        }

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> operationIds = scopeAllPerms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> operationMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);

        Map<Integer, List<RoleResourcePermission>> scopeAllByType = scopeAllPerms.stream()
            .filter(p -> p.getResourceType() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceType));

        // Batch resolve resource type codes (avoid N+1)
        Set<Integer> resourceTypeValues = scopeAllByType.keySet();
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        for (Map.Entry<Integer, List<RoleResourcePermission>> scopeEntry : scopeAllByType.entrySet()) {
            Integer resourceType = scopeEntry.getKey();
            String resolvedResourceTypeCode = resourceTypeCodeMap.get(resourceType);
            List<String> allowedOps = scopeEntry.getValue().stream()
                .map(p -> operationMap.get(p.getOperationPermissionId()))
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
                ResourceEntry entry = buildScopeAllResourceEntry(tenantId, r, scopeEntry.getValue(),
                    resolvedResourceTypeCode, allowedOps, context, items);
                if (entry != null) {
                    items.add(entry);
                }
            }
        }
    }

    private ResourceEntry buildScopeAllResourceEntry(Long tenantId, ResourceEntity r,
                                                      List<RoleResourcePermission> scopeAllPerms,
                                                      String resolvedResourceTypeCode, List<String> allowedOps,
                                                      QueryResourcesContext context, List<ResourceEntry> existingItems) {
        if (context.requiredCodeType != null && !context.requiredCodeType.isBlank() && !context.requiredCodeType.equals(r.getCodeType())) {
            return null;
        }
        if (!context.includeChildren && r.getParentId() != null) {
            return null;
        }
        if (context.resourceCodePrefix != null && !context.resourceCodePrefix.isBlank()
            && (r.getCode() == null || !r.getCode().startsWith(context.resourceCodePrefix))) {
            return null;
        }
        if (existingItems.stream().anyMatch(item -> Objects.equals(item.resourceCode(), r.getCode())
            && Objects.equals(item.resourceTypeCode(), resolvedResourceTypeCode))) {
            return null;
        }
        List<Long> matchedRoleIds = scopeAllPerms.stream()
            .map(RoleResourcePermission::getAbstractRoleId).filter(Objects::nonNull).distinct().toList();
        List<Long> matchedPermissionIds = scopeAllPerms.stream()
            .map(RoleResourcePermission::getId).filter(Objects::nonNull).distinct().toList();
        List<String> grantSources = scopeAllPerms.stream()
            .map(RoleResourcePermission::getGrantSource).filter(Objects::nonNull).distinct().toList();
        boolean canGrant = scopeAllPerms.stream().anyMatch(p -> Boolean.TRUE.equals(p.getCanGrant()));
        return new ResourceEntry(
            resolvedResourceTypeCode, r.getCode(), r.getCodeType(), r.getName(),
            canGrant, allowedOps, matchedRoleIds, matchedPermissionIds, grantSources
        );
    }

    private QueryResourcesResp buildQueryResourcesResponse(List<ResourceEntry> items, Long userId,
                                                            Long tenantId, Set<Long> validRoleIds,
                                                            QueryResourcesReq req) {
        if (validRoleIds.isEmpty()) {
            return new QueryResourcesResp(List.of(), "", 60);
        }

        List<ResourceEntry> resultItems = items;
        if (Boolean.TRUE.equals(req.treeMode())) {
            resultItems = items.stream()
                .sorted(Comparator.comparing(ResourceEntry::resourceCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        }

        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(userId, tenantId, validRoleIds);
        return new QueryResourcesResp(resultItems, permissionVersion, 60);
    }

    @Override
    @Transactional(readOnly = true)
    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        // 1. 参数验证
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryScopesResp(false, "USER_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);

        Long parentResourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.parentResourceTypeCode(), req.parentResourceCode(), req.parentCodeType(), req.domainCode());
        if (parentResourceEntityId == null) {
            return new QueryScopesResp(false, "OBJECT_KEY_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        // 2. 准备上下文
        QueryScopesContext context = prepareQueryScopesContext(tenantId, userId, req);

        // 3. 验证父权限
        ParentPermissionsResult parentResult = validateParentPermissions(tenantId, context, req, parentResourceEntityId);
        if (parentResult == null) {
            return new QueryScopesResp(false, "NO_PERMISSION", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        // 4. 处理范围权限
        Map<String, ScopeAccumulator> merged = processScopePermissions(tenantId, context, req, parentResult.parentPermissionIds);

        // 5. 构建响应
        return buildQueryScopesResponse(merged, context, parentResult);
    }

    /**
     * 查询范围权限上下文
     */
    private static class QueryScopesContext {
        final Long tenantId;
        final Long userId;
        final Long bizDomainId;
        final Set<Long> validRoleIds;
        final Map<String, Object> context;

        QueryScopesContext(Long tenantId, Long userId, Long bizDomainId, Set<Long> validRoleIds, Map<String, Object> context) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.bizDomainId = bizDomainId;
            this.validRoleIds = validRoleIds;
            this.context = context;
        }
    }

    /**
     * 父权限验证结果
     */
    private static class ParentPermissionsResult {
        final Set<String> matchedParentOps;
        final Set<Long> parentPermissionIds;

        ParentPermissionsResult(Set<String> matchedParentOps, Set<Long> parentPermissionIds) {
            this.matchedParentOps = matchedParentOps;
            this.parentPermissionIds = parentPermissionIds;
        }
    }

    private QueryScopesContext prepareQueryScopesContext(Long tenantId, Long userId, QueryScopesReq req) {
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) {
            return new QueryScopesContext(tenantId, userId, bizDomainId, Set.of(), Map.of());
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return new QueryScopesContext(tenantId, userId, bizDomainId, Set.of(), Map.of());
        }
        Map<String, Object> context = req.context() != null ? req.context() : Map.of();
        return new QueryScopesContext(tenantId, userId, bizDomainId, validRoleIds, context);
    }

    private ParentPermissionsResult validateParentPermissions(Long tenantId, QueryScopesContext context,
                                                               QueryScopesReq req, Long parentResourceEntityId) {
        if (context.validRoleIds.isEmpty()) {
            return null;
        }

        // Batch resolve operation codes (avoid N+1)
        Set<String> parentOpCodes = new HashSet<>(req.parentOperationCodes());
        Map<String, Long> parentOpIdMap = typeResolutionService.batchResolveOperationIds(
            tenantId, req.parentResourceTypeCode(), parentOpCodes);

        List<RolePermEntry> parentEntries = new ArrayList<>();
        Set<String> matchedParentOps = new HashSet<>();
        for (String parentOpCode : req.parentOperationCodes()) {
            Long parentOpId = parentOpIdMap.get(parentOpCode);
            if (parentOpId == null) {
                continue;
            }
            List<RolePermEntry> oneOpEntries = queryMatchedEntries(
                tenantId, context.validRoleIds, parentResourceEntityId, parentOpId, null
            );
            oneOpEntries = permissionConditionDomainService.evaluate(tenantId, oneOpEntries, context.context);
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
            return null;
        }
        return new ParentPermissionsResult(matchedParentOps, parentPermissionIds);
    }

    private Map<String, ScopeAccumulator> processScopePermissions(Long tenantId, QueryScopesContext context,
                                                                   QueryScopesReq req, Set<Long> parentPermissionIds) {
        // Batch resolve scope resource type codes (avoid N+1)
        Set<String> scopeTypeCodes = new HashSet<>(req.scopeResourceTypeCodes());
        Map<String, Integer> scopeTypeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", scopeTypeCodes);

        // Batch resolve scope operation codes by type (avoid N+1)
        Map<String, Map<String, Long>> scopeOpIdMapByType = new HashMap<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            Set<String> scopeOpCodes = new HashSet<>(req.scopeOperationCodes());
            Map<String, Long> opMap = typeResolutionService.batchResolveOperationIds(tenantId, scopeTypeCode, scopeOpCodes);
            scopeOpIdMapByType.put(scopeTypeCode, opMap);
        }

        Map<String, ScopeAccumulator> merged = new LinkedHashMap<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            Integer scopeType = scopeTypeValueMap.get(scopeTypeCode);
            if (scopeType == null) {
                continue;
            }
            List<RoleResourcePermission> scopePermCandidates = rolePermMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(context.validRoleIds))
                    .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(scopeType))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            // Batch load resource entities for entries to avoid N+1 queries
            List<RolePermEntry> allScopeEntries = toRolePermEntries(tenantId, scopePermCandidates);
            Set<Long> scopeResourceIds = allScopeEntries.stream()
                .map(RolePermEntry::resourceEntityId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
            Map<Long, ResourceEntity> scopeResourceMap = entityBatchLoadDomainService.batchLoadResources(tenantId, scopeResourceIds);

            Map<String, Long> scopeOpIdMap = scopeOpIdMapByType.getOrDefault(scopeTypeCode, Map.of());
            processScopeOperations(tenantId, context, req, scopeTypeCode, scopeOpIdMap, scopeOpId -> allScopeEntries.stream()
                .filter(entry -> entry.operationPermissionId().equals(scopeOpId))
                .filter(entry -> entry.dependOn() == null || parentPermissionIds.contains(entry.dependOn()))
                .toList(), scopeResourceMap, merged);
        }
        return merged;
    }

    private void processScopeOperations(Long tenantId, QueryScopesContext context, QueryScopesReq req,
                                         String scopeTypeCode, Map<String, Long> scopeOpIdMap,
                                         java.util.function.Function<Long, List<RolePermEntry>> entriesSupplier,
                                         Map<Long, ResourceEntity> scopeResourceMap, Map<String, ScopeAccumulator> merged) {
        for (String scopeOpCode : req.scopeOperationCodes()) {
            Long scopeOpId = scopeOpIdMap.get(scopeOpCode);
            if (scopeOpId == null) {
                continue;
            }
            List<RolePermEntry> entries = entriesSupplier.apply(scopeOpId);
            entries = permissionConditionDomainService.evaluate(tenantId, entries, context.context);
            entries = permissionConflictDomainService.filterPermMutex(tenantId, entries);

            for (RolePermEntry entry : entries) {
                ScopeAccumulator accumulator = buildScopeAccumulator(entry, scopeTypeCode, scopeResourceMap, merged);
                if (accumulator == null) {
                    continue;
                }
                accumulator.operations.add(scopeOpCode);
                String source = entry.dependOn() == null ? "DIRECT" : "DEPENDENT";
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

    private ScopeAccumulator buildScopeAccumulator(RolePermEntry entry, String scopeTypeCode,
                                                    Map<Long, ResourceEntity> scopeResourceMap,
                                                    Map<String, ScopeAccumulator> merged) {
        boolean scopeAll = entry.resourceEntityId() == null;
        ResourceEntity resource = scopeAll ? null : scopeResourceMap.get(entry.resourceEntityId());
        if (!scopeAll && (resource == null || resource.getDeleteFlag() != 0L)) {
            return null;
        }
        String mergeKey = scopeAll
            ? "ALL|" + scopeTypeCode
            : "ONE|" + scopeTypeCode + "|" + resource.getCodeType() + "|" + resource.getCode();
        return merged.computeIfAbsent(
            mergeKey,
            key -> new ScopeAccumulator(
                scopeTypeCode,
                scopeAll ? null : resource.getCode(),
                scopeAll ? null : resource.getCodeType(),
                scopeAll ? null : resource.getName(),
                scopeAll
            )
        );
    }

    private QueryScopesResp buildQueryScopesResponse(Map<String, ScopeAccumulator> merged,
                                                      QueryScopesContext context,
                                                      ParentPermissionsResult parentResult) {
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
        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(context.userId, context.tenantId, context.validRoleIds);
        return new QueryScopesResp(
            true,
            null,
            new ArrayList<>(parentResult.matchedParentOps),
            new ArrayList<>(parentResult.parentPermissionIds),
            items,
            "UNION",
            permissionVersion,
            60
        );
    }

    private List<RolePermEntry> toRolePermEntries(Long tenantId, List<RoleResourcePermission> perms) {
        if (perms.isEmpty()) {
            return List.of();
        }
        // Batch load operation permissions to avoid N+1 queries
        Set<Long> opIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opCache = entityBatchLoadDomainService.batchLoadOperations(tenantId, opIds);
        List<RolePermEntry> entries = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = opCache.get(perm.getOperationPermissionId());
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
                perm.getCanGrant(),
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
        long currentVersion = permissionVersionDomainService.calculateMaxVersion(tenantId, validRoleIds);
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
        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.ID.eq(userId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        if (user == null) return AuthCheckResp.deny("USER_NOT_FOUND");
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
            List<Long> parentIds = resourceEntityDomainService.getAncestorIds(tenantId, resourceEntityId);
            if (!parentIds.isEmpty()) {
                targetResourceIds.addAll(parentIds);
            }
        }
        if ("CHILDREN".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> childIds = resourceEntityDomainService.getDescendantIds(tenantId, resourceEntityId);
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
        if (targetOp == null) return List.of();

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> grantedOpIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        grantedOpIds.add(operationPermissionId);
        Map<Long, OperationPermission> opCache = new HashMap<>(entityBatchLoadDomainService.batchLoadOperations(tenantId, grantedOpIds));
        opCache.put(operationPermissionId, targetOp);

        List<RolePermEntry> result = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = opCache.get(perm.getOperationPermissionId());
            if (grantedOp == null) continue;

            if (grantedOp.matchesBit(targetOp)) {
                result.add(new RolePermEntry(
                    perm.getId(), perm.getAbstractRoleId(),
                    perm.getResourceEntityId(), null, perm.getResourceType(),
                    perm.getOperationPermissionId(), grantedOp.getCode(), null,
                    perm.getGrantSource(),
                    perm.getCanGrant(), perm.getConditionId(), perm.getConditionId() != null,
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
        // AntPathMatcher 支持 *、**、{xxx} 三种通配符
        return PATH_MATCHER.match(pattern, path);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req) {
        // 1. 参数验证和上下文准备
        TreeContext context = prepareTreeContext(tenantId, req);
        if (context.userId == null) {
            return new PermissionTreeResp(null, List.of(), List.of(), null, 60);
        }
        if (context.rootResourceId == null) {
            return new PermissionTreeResp(null, List.of(), List.of(), null, 60);
        }
        if (context.validRoleIds.isEmpty()) {
            ResourceEntity rootResource = resourceEntityMapper.selectOneById(context.rootResourceId);
            return new PermissionTreeResp(buildNode(context.rootResourceId, 0, Set.of(), false, null, rootResource, Map.of()),
                List.of(), List.of(), null, 60);
        }

        // 2. 构建权限映射
        Map<Long, List<RoleResourcePermission>> permissionMap = buildPermissionMap(tenantId, context);

        // 3. 构建树
        return buildPermissionTreeResponse(tenantId, context, permissionMap);
    }

    /**
     * 权限树查询上下文
     */
    private static class TreeContext {
        final Long userId;
        final Long rootResourceId;
        final Long bizDomainId;
        final Set<Long> validRoleIds;
        final Set<Long> operationIds;
        final Map<Long, OperationPermission> operationMap;
        final int maxDepth;
        final String direction;

        TreeContext(Long userId, Long rootResourceId, Long bizDomainId, Set<Long> validRoleIds,
                    Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                    int maxDepth, String direction) {
            this.userId = userId;
            this.rootResourceId = rootResourceId;
            this.bizDomainId = bizDomainId;
            this.validRoleIds = validRoleIds;
            this.operationIds = operationIds;
            this.operationMap = operationMap;
            this.maxDepth = maxDepth;
            this.direction = direction;
        }
    }

    private TreeContext prepareTreeContext(Long tenantId, PermissionTreeReq req) {
        // 1. Resolve subject to internal user ID
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new TreeContext(null, null, null, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 2. Resolve starting resource
        Long rootResourceId = typeResolutionService.resolveResourceId(
            tenantId, req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.domainCode()
        );
        if (rootResourceId == null) {
            return new TreeContext(userId, null, null, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 3. Resolve bizDomainId from domainCode
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());

        // 4. Get effective roles
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, bizDomainId, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, bizDomainId, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 5. Resolve operation permission IDs for requested operation codes
        Set<Long> operationIds = resolveOperationIds(tenantId, req);

        // Batch load operation permissions for getOperationsForResource calls
        Map<Long, OperationPermission> operationMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);

        int maxDepth = req.maxDepth() != null ? req.maxDepth() : 10;
        String direction = req.direction() != null ? req.direction().toUpperCase() : "BOTH";

        return new TreeContext(userId, rootResourceId, bizDomainId, validRoleIds, operationIds, operationMap, maxDepth, direction);
    }

    private Set<Long> resolveOperationIds(Long tenantId, PermissionTreeReq req) {
        // Batch resolve operation codes (avoid N+1)
        Set<String> opCodes = new HashSet<>(req.operationCodes());
        Map<String, Long> opIdMap = typeResolutionService.batchResolveOperationIds(tenantId, req.resourceTypeCode(), opCodes);
        return new HashSet<>(opIdMap.values());
    }

    private Map<Long, List<RoleResourcePermission>> buildPermissionMap(Long tenantId, TreeContext context) {
        // Get all permissions for valid roles
        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(context.validRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Build permission map by resource entity ID
        return allPerms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));
    }

    private PermissionTreeResp buildPermissionTreeResponse(Long tenantId, TreeContext context,
                                                            Map<Long, List<RoleResourcePermission>> permsByResource) {
        // Batch load all related resources for tree traversal (avoid N+1)
        List<ResourceEntity> allResources = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        Map<Long, ResourceEntity> allResourceMap = allResources.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // Batch resolve all resource type codes (avoid N+1)
        Set<Integer> allResourceTypes = allResources.stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", allResourceTypes);

        // Build root node
        ResourceEntity rootResource = allResourceMap.get(context.rootResourceId);
        TreeNode root = buildNode(context.rootResourceId, 0,
            getOperationsForResource(permsByResource.get(context.rootResourceId), context.operationIds, context.operationMap),
            hasCanGrant(permsByResource.get(context.rootResourceId), context.operationIds),
            rootResource != null ? rootResource.getName() : null,
            rootResource, resourceTypeCodeMap);

        // Traverse based on direction
        List<TreeNode> ancestors = List.of();
        List<TreeNode> descendants = List.of();

        if ("ANCESTORS".equals(context.direction) || "BOTH".equals(context.direction)) {
            ancestors = traverseAncestors(tenantId, context.rootResourceId, permsByResource,
                context.operationIds, context.operationMap, context.maxDepth, allResourceMap, resourceTypeCodeMap);
        }
        if ("DESCENDANTS".equals(context.direction) || "BOTH".equals(context.direction)) {
            descendants = traverseDescendants(tenantId, context.rootResourceId, permsByResource,
                context.operationIds, context.operationMap, context.maxDepth, allResourceMap, resourceTypeCodeMap);
        }

        // Build permission version
        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(context.userId, tenantId, context.validRoleIds);

        return new PermissionTreeResp(root, ancestors, descendants, permissionVersion, 60);
    }

    private TreeNode buildNode(Long resourceId, int depth,
                               Set<String> operations, boolean canGrant, String name,
                               ResourceEntity resource, Map<Integer, String> resourceTypeCodeMap) {
        if (resource == null) {
            return new TreeNode(resourceId, null, null, name, depth, operations, canGrant, null);
        }
        String typeCode = resourceTypeCodeMap.get(resource.getResourceType());
        return new TreeNode(resourceId, typeCode, resource.getCode(), resource.getName(), depth, operations, canGrant, null);
    }

    private Set<String> getOperationsForResource(List<RoleResourcePermission> perms, Set<Long> operationIds,
                                                  Map<Long, OperationPermission> operationMap) {
        if (perms == null || perms.isEmpty()) return Set.of();
        return perms.stream()
            .filter(p -> operationIds.contains(p.getOperationPermissionId()))
            .map(p -> {
                OperationPermission op = operationMap.get(p.getOperationPermissionId());
                return op != null ? op.getCode() : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private boolean hasCanGrant(List<RoleResourcePermission> perms, Set<Long> operationIds) {
        if (perms == null || perms.isEmpty()) return false;
        return perms.stream()
            .filter(p -> operationIds.contains(p.getOperationPermissionId()))
            .anyMatch(p -> Boolean.TRUE.equals(p.getCanGrant()));
    }

    private List<TreeNode> traverseAncestors(Long tenantId, Long startResourceId,
                                              Map<Long, List<RoleResourcePermission>> permsByResource,
                                              Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                              int maxDepth, Map<Long, ResourceEntity> allResourceMap,
                                              Map<Integer, String> resourceTypeCodeMap) {
        List<TreeNode> ancestors = new ArrayList<>();
        Long currentId = startResourceId;
        int depth = -1;

        while (currentId != null && Math.abs(depth) <= maxDepth) {
            ResourceEntity resource = allResourceMap.get(currentId);
            if (resource == null || resource.getDeleteFlag() != 0L || !resource.getTenantId().equals(tenantId)) {
                break;
            }
            if (resource.getParentId() != null) {
                List<RoleResourcePermission> perms = permsByResource.get(resource.getParentId());
                Set<String> ops = getOperationsForResource(perms, operationIds, operationMap);
                if (!ops.isEmpty()) {
                    ResourceEntity parentResource = allResourceMap.get(resource.getParentId());
                    TreeNode node = buildNode(resource.getParentId(), depth, ops,
                        hasCanGrant(perms, operationIds), null, parentResource, resourceTypeCodeMap);
                    ancestors.add(node);
                }
                currentId = resource.getParentId();
                depth--;
            } else {
                break;
            }
        }
        return ancestors;
    }

    private List<TreeNode> traverseDescendants(Long tenantId, Long startResourceId,
                                                Map<Long, List<RoleResourcePermission>> permsByResource,
                                                Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                int maxDepth, Map<Long, ResourceEntity> allResourceMap,
                                                Map<Integer, String> resourceTypeCodeMap) {
        List<TreeNode> descendants = new ArrayList<>();
        collectDescendantsWithPermission(tenantId, startResourceId, permsByResource, operationIds, operationMap,
            1, maxDepth, descendants, allResourceMap, resourceTypeCodeMap);
        return descendants;
    }

    private void collectDescendantsWithPermission(Long tenantId, Long parentId,
                                                   Map<Long, List<RoleResourcePermission>> permsByResource,
                                                   Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                   int currentDepth, int maxDepth,
                                                   List<TreeNode> result,
                                                   Map<Long, ResourceEntity> allResourceMap,
                                                   Map<Integer, String> resourceTypeCodeMap) {
        if (currentDepth > maxDepth) return;

        // Filter children from pre-loaded resources
        List<ResourceEntity> children = allResourceMap.values().stream()
            .filter(r -> Objects.equals(r.getParentId(), parentId) && r.getDeleteFlag() == 0L && r.getTenantId().equals(tenantId))
            .collect(Collectors.toList());

        for (ResourceEntity child : children) {
            List<RoleResourcePermission> perms = permsByResource.get(child.getId());
            Set<String> ops = getOperationsForResource(perms, operationIds, operationMap);
            if (!ops.isEmpty()) {
                TreeNode node = buildNode(child.getId(), currentDepth, ops,
                    hasCanGrant(perms, operationIds), child.getName(), child, resourceTypeCodeMap);
                result.add(node);
            }
            collectDescendantsWithPermission(tenantId, child.getId(), permsByResource, operationIds, operationMap,
                currentDepth + 1, maxDepth, result, allResourceMap, resourceTypeCodeMap);
        }
    }
}
