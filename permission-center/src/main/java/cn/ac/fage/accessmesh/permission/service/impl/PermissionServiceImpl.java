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
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                                 PermCacheDomainService permCacheDomainService) {
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
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND"))
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
                item.resourceTypeCode(), item.resourceCode(), item.operationCode(), resp.allowed(), resp.reason()));
        }
        return new BatchAuthCheckResp(results);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        AbstractUser user = abstractUserMapper.selectOneById(req.userId());
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

        ResourceApiMapping matchedMapping = null;
        for (ResourceApiMapping mapping : mappings) {
            if (pathMatches(mapping.getPathPattern(), req.path())) {
                matchedMapping = mapping;
                break;
            }
        }
        if (matchedMapping == null) {
            return CheckInterfaceResp.deny("API_NOT_REGISTERED");
        }

        ResourceEntity resource = resourceEntityMapper.selectOneById(matchedMapping.getResourceEntityId());
        if (resource == null || resource.getDeleteFlag() != 0L) {
            return CheckInterfaceResp.deny("RESOURCE_NOT_FOUND");
        }

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, req.userId(), null);
        if (effectiveRoleIds.isEmpty()) {
            return CheckInterfaceResp.deny("NO_ROLE");
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return CheckInterfaceResp.deny("NO_ROLE");
        }

        List<OperationPermission> allOps = operationPermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(resource.getResourceType()))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        Map<String, Object> context = req.context() != null ? req.context() : Map.of();
        for (OperationPermission op : allOps) {
            List<RolePermEntry> entries = queryMatchedEntries(tenantId, validRoleIds,
                matchedMapping.getResourceEntityId(), op.getId(), null);
            if (!entries.isEmpty()) {
                List<RolePermEntry> passed = permissionConditionDomainService.evaluate(tenantId, entries, context);
                if (!passed.isEmpty()) {
                    List<RolePermEntry> final_ = permissionConflictDomainService.filterPermMutex(tenantId, passed);
                    if (!final_.isEmpty()) {
                        return CheckInterfaceResp.allow(final_.get(0).resourceEntityId(), op.getCode());
                    }
                }
            }
        }
        return CheckInterfaceResp.deny("NO_PERMISSION");
    }

    @Override
    @Transactional(readOnly = true)
    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), false);

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", req.resourceTypeCode());
        if (resourceType == null) return new QueryResourcesResp(List.of(), false);

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) return new QueryResourcesResp(List.of(), false);
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new QueryResourcesResp(List.of(), false);

        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
            .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(resourceType))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        if (req.canManageOnly()) {
            qw.and(ROLE_RESOURCE_PERMISSION.CAN_MANAGE.eq(true));
        }

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(qw);
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId).collect(Collectors.toSet());

        List<ResourceEntry> items = new ArrayList<>();
        for (Long rid : resourceIds) {
            ResourceEntity r = resourceEntityMapper.selectOneById(rid);
            if (r == null || r.getDeleteFlag() != 0L) continue;
            if ("*".equals(r.getCode())) return new QueryResourcesResp(List.of(), true);
            boolean canManage = perms.stream()
                .anyMatch(p -> p.getResourceEntityId().equals(rid) && Boolean.TRUE.equals(p.getCanManage()));
            items.add(new ResourceEntry(req.resourceTypeCode(), r.getCode(), r.getCodeType(), r.getName(), canManage));
        }
        return new QueryResourcesResp(items, false);
    }

    @Override
    @Transactional(readOnly = true)
    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryScopesResp(false, List.of());

        Long resourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.domainCode());
        if (resourceEntityId == null) return new QueryScopesResp(false, List.of());

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Long operationId = typeResolutionService.resolveOperationId(tenantId, req.operationCode(), req.resourceTypeCode());
        if (operationId == null) return new QueryScopesResp(false, List.of());

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) return new QueryScopesResp(false, List.of());
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new QueryScopesResp(false, List.of());

        List<RolePermEntry> directEntries = queryMatchedEntries(tenantId, validRoleIds, resourceEntityId, operationId, null);
        directEntries = permissionConditionDomainService.evaluate(tenantId, directEntries, Map.of());
        directEntries = permissionConflictDomainService.filterPermMutex(tenantId, directEntries);

        // scope_all: there is a direct grant with no dependOn (full scope)
        boolean scopeAll = directEntries.stream().anyMatch(e -> e.dependOn() == null);
        if (scopeAll) return new QueryScopesResp(true, List.of());

        // Dependent resource IDs from resource_dependency
        List<Long> dependentResourceIds = resourceDependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        ).stream().map(dep -> dep.getDependsOnResourceEntityId()).collect(Collectors.toList());

        List<ScopeEntry> items = new ArrayList<>();
        for (RolePermEntry entry : directEntries) {
            if (entry.dependOn() != null) {
                ResourceEntity r = resourceEntityMapper.selectOneById(entry.dependOn());
                if (r != null && r.getDeleteFlag() == 0L) {
                    items.add(new ScopeEntry(req.resourceTypeCode(), r.getCode(), r.getCodeType(), r.getName(), "DIRECT"));
                }
            }
        }
        for (Long depId : dependentResourceIds) {
            ResourceEntity r = resourceEntityMapper.selectOneById(depId);
            if (r != null && r.getDeleteFlag() == 0L) {
                boolean alreadyDirect = items.stream().anyMatch(i -> i.resourceCode().equals(r.getCode()));
                if (!alreadyDirect) {
                    items.add(new ScopeEntry(req.resourceTypeCode(), r.getCode(), r.getCodeType(), r.getName(), "DEPENDENT"));
                }
            }
        }
        return new QueryScopesResp(false, items);
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

        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Set<Long> allowedResourceIds = allPerms.stream()
            .map(RoleResourcePermission::getResourceEntityId).collect(Collectors.toSet());

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
        return new InterfaceSnapshotResp(false, 0, entries);
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
        Long matchedPermId = finalEntries.get(0).operationPermissionId();
        return AuthCheckResp.allow(null, matchedPermId, conditionEvaluated);
    }

    private List<RolePermEntry> queryMatchedEntries(Long tenantId, Set<Long> roleIds,
                                                    Long resourceEntityId, Long operationPermissionId,
                                                    String inheritMode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
            .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));

        if ("PARENT".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> parentIds = getAncestorIds(tenantId, resourceEntityId);
            if (!parentIds.isEmpty()) {
                qw.or(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(parentIds));
            }
        }
        if ("CHILDREN".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            List<Long> childIds = getDescendantIds(tenantId, resourceEntityId);
            if (!childIds.isEmpty()) {
                qw.or(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(childIds));
            }
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
            if (targetBit == 0L || (effectiveBits & targetBit) != 0) {
                result.add(new RolePermEntry(
                    perm.getResourceEntityId(), null, perm.getResourceType(),
                    perm.getOperationPermissionId(), grantedOp.getCode(), null,
                    perm.getCanManage(), perm.getConditionId(), perm.getConditionId() != null,
                    perm.getDependOn()));
            }
        }
        return result;
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
