package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserEffectiveRolesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp.RoleGrantInfo;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp.PermissionItem;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp.ResourcePermissionView;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp.SourceRoleView;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.LogQueryService;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import cn.ac.fage.accessmesh.permission.service.context.PermissionQueryContext;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef.BIZ_DOMAIN;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class PermissionViewServiceImpl implements PermissionViewService {

    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final BizDomainMapper bizDomainMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final UserRoleDomainService userRoleDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermissionService permissionService;
    private final LogQueryService logQueryService;
    private final ObjectMapper objectMapper;

    public PermissionViewServiceImpl(AbstractUserMapper abstractUserMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     ResourceEntityMapper resourceEntityMapper,
                                     OperationPermissionMapper operationPermissionMapper,
                                     BizDomainMapper bizDomainMapper,
                                     RoleResourcePermissionMapper rolePermMapper,
                                     UserRoleDomainService userRoleDomainService,
                                     TypeResolutionService typeResolutionService,
                                     PermissionService permissionService,
                                     LogQueryService logQueryService,
                                     ObjectMapper objectMapper) {
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleDomainService = userRoleDomainService;
        this.typeResolutionService = typeResolutionService;
        this.permissionService = permissionService;
        this.logQueryService = logQueryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserPermissionViewResp getUserPermissions(Long tenantId, Long userId) {
        PaginatedResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, new UserPermissionViewReq(
            PermConstants.TargetType.USER, null, null, null, null, null, null, null, null, null,
            false, false, true, 20, 1, 50
        ));
        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.ID.eq(userId))
                .and(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        if (user == null) {
            return null;
        }
        String subjectTypeCode = typeResolutionService.resolveTypeCode(tenantId, "user_type", user.getUserType());
        if (subjectTypeCode == null) {
            subjectTypeCode = PermConstants.TargetType.USER;
        }
        return new UserPermissionViewResp(subjectTypeCode, user.getExternalId(), user.getName(), paged.items());
    }

    @Override
    public PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType())) {
            Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
            if (userId == null) {
                return new PermissionEffectivePermissionsResp(PermConstants.TargetType.USER, List.of(), 0, pageNum, pageSize, false);
            }
            PaginatedResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, req);
            List<PermissionEffectivePermissionsResp.EffectivePermissionItem> items = paged.items().stream().map(v ->
                new PermissionEffectivePermissionsResp.EffectivePermissionItem(
                    v.resourceTypeCode(), v.resourceCode(), v.resourceName(), v.codeType(),
                    v.operationCodes(), v.scopeAll(),
                    v.sourceRoles() == null ? List.of() : v.sourceRoles().stream().map(sr ->
                        new PermissionEffectivePermissionsResp.SourceRole(sr.roleTypeCode(), sr.roleExternalId(), sr.roleName(), sr.via())
                    ).toList(),
                    v.sourceRoleCount(), v.sourceRolesTruncated(), v.matchedPermissionIds()
                )
            ).toList();
            return new PermissionEffectivePermissionsResp(PermConstants.TargetType.USER, items, (int) paged.total(), pageNum, pageSize, paged.hasNext());
        }
        PaginatedResp<PermissionItem> paged = getRolePermissionItemsPaged(
            tenantId, req.domainCode(), req.roleTypeCode(), req.roleExternalId(), pageNum, pageSize);
        List<PermissionEffectivePermissionsResp.EffectivePermissionItem> items = paged.items().stream().map(p ->
            new PermissionEffectivePermissionsResp.EffectivePermissionItem(
                p.resourceTypeCode(), p.resourceCode(), p.resourceName(), null,
                p.operationCode() == null ? List.of() : List.of(p.operationCode()),
                false, List.of(), 0, false,
                p.id() == null ? List.of() : List.of(p.id())
            )
        ).toList();
        return new PermissionEffectivePermissionsResp(PermConstants.TargetType.ROLE, items, (int) paged.total(), pageNum, pageSize, paged.hasNext());
    }

    PaginatedResp<ResourcePermissionView> getUserPermissionsWithFilters(Long tenantId, Long userId, UserPermissionViewReq req) {
        PermissionQueryContext context = new PermissionQueryContext(tenantId, userId, req);

        prepareContext(context);
        if (context.isUserNotFound()) {
            return emptyResponse(context);
        }

        loadRoles(context);
        if (context.hasNoFilteredRoles()) {
            return emptyResponse(context);
        }

        loadPermissions(context);
        if (context.hasNoPermissions()) {
            return emptyResponse(context);
        }

        filterPermissions(context);
        paginateResults(context);
        return assembleResponse(context);
    }

    /**
     * Stage 1: Prepare context with user and pagination info.
     */
    private void prepareContext(PermissionQueryContext context) {
        context.setPageNum(PageUtil.pageNum(context.getRequest().pageNum()));
        context.setPageSize(PageUtil.pageSize(context.getRequest().pageSize()));
        context.setOffset(Math.max((context.getPageNum() - 1) * context.getPageSize(), 0));

        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.ID.eq(context.getUserId()))
                .and(ABSTRACT_USER.TENANT_ID.eq(context.getTenantId()))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        context.setUser(user);

        if (user != null) {
            Long domainId = typeResolutionService.resolveDomainId(context.getTenantId(), context.getRequest().domainCode());
            context.setDomainId(domainId);
        }
    }

    /**
     * Stage 2: Load and filter user roles.
     */
    private void loadRoles(PermissionQueryContext context) {
        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(
            context.getTenantId(), context.getUserId(), context.getDomainId());
        context.setRoleIds(roleIds);

        if (context.hasNoRoles()) {
            context.setFilteredRoleIds(Set.of());
            return;
        }

        Set<Long> filteredRoleIds = filterRoleIds(
            context.getTenantId(), roleIds,
            context.getRequest().sourceRoleExternalId(),
            context.getRequest().roleTypeCode(),
            context.getRequest().domainCode()
        );
        context.setFilteredRoleIds(filteredRoleIds);

        if (!context.hasNoFilteredRoles()) {
            Map<Long, AbstractRole> roleMap = loadRoles(context.getTenantId(), filteredRoleIds);
            context.setRoleMap(roleMap);
        }
    }

    /**
     * Stage 3: Load all permissions for filtered roles and related entities.
     */
    private void loadPermissions(PermissionQueryContext context) {
        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(context.getTenantId()))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(context.getFilteredRoleIds()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        context.setAllPermissions(allPerms);

        if (allPerms.isEmpty()) {
            return;
        }

        // Batch load related entities
        Set<Long> operationIds = allPerms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> operationMap = loadOperations(operationIds);
        context.setOperationMap(operationMap);

        Set<Long> resourceIds = allPerms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(context.getTenantId(), resourceIds);
        context.setResourceMap(resourceMap);

        Set<Long> domainIds = resourceMap.values().stream()
            .map(ResourceEntity::getBizDomainId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> domainCodeMap = loadDomainCodes(context.getTenantId(), domainIds);
        context.setDomainCodeMap(domainCodeMap);

        // Resolve API type value for filtering
        Integer apiTypeValue = typeResolutionService.resolveTypeValue(context.getTenantId(), "resource_type", ResourceTypeCode.API);
        context.setApiTypeValue(apiTypeValue);

        // Batch resolve resource type codes
        Set<Integer> allResourceTypes = allPerms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        allResourceTypes.addAll(resourceMap.values().stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(
            context.getTenantId(), "resource_type", allResourceTypes);
        context.setResourceTypeCodeMap(resourceTypeCodeMap);

        // Batch resolve role type codes
        Set<Integer> roleTypeValues = context.getRoleMap().values().stream()
            .map(AbstractRole::getRoleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> roleTypeCodeMap = typeResolutionService.batchResolveTypeCodes(
            context.getTenantId(), "role_type", roleTypeValues);
        context.setRoleTypeCodeMap(roleTypeCodeMap);
    }

    /**
     * Stage 4: Filter permissions based on request criteria.
     */
    private void filterPermissions(PermissionQueryContext context) {
        List<RoleResourcePermission> filteredPerms = context.getAllPermissions().stream()
            .filter(p -> matchesFilters(p, context))
            .toList();
        context.setFilteredPermissions(filteredPerms);
    }

    /**
     * Stage 5: Paginate filtered results and group by resource.
     */
    private void paginateResults(PermissionQueryContext context) {
        List<RoleResourcePermission> filteredPerms = context.getFilteredPermissions();
        long total = filteredPerms.size();
        context.setTotalCount(total);

        List<RoleResourcePermission> pagePerms = filteredPerms.stream()
            .skip(context.getOffset())
            .limit(context.getPageSize())
            .toList();
        context.setPagedPermissions(pagePerms);

        Map<Long, List<RoleResourcePermission>> byResource = pagePerms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));
        context.setGroupedByResource(byResource);

        context.setHasNext(context.getOffset() + context.getPageSize() < total);
    }

    /**
     * Stage 6: Assemble the final response.
     */
    private PaginatedResp<ResourcePermissionView> assembleResponse(PermissionQueryContext context) {
        int sourceRoleLimit = context.getSourceRoleLimit();
        boolean includeSourceRoles = context.shouldIncludeSourceRoles();

        List<ResourcePermissionView> resourceViews = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : context.getGroupedByResource().entrySet()) {
            ResourceEntity resource = context.getResourceMap().get(entry.getKey());
            if (resource == null) {
                continue;
            }

            ResourcePermissionView view = buildResourcePermissionView(
                entry.getKey(), entry.getValue(), resource, context, sourceRoleLimit, includeSourceRoles
            );
            resourceViews.add(view);
        }

        return new PaginatedResp<>(
            resourceViews,
            context.getTotalCount(),
            context.getPageNum(),
            context.getPageSize(),
            context.isHasNext()
        );
    }

    /**
     * Check if a permission matches the filter criteria.
     */
    private boolean matchesFilters(RoleResourcePermission perm, PermissionQueryContext context) {
        // Filter by scope
        if (!context.shouldIncludeScopes() && perm.getDependOn() != null) {
            return false;
        }

        // Filter by operation codes
        if (context.hasOperationCodesFilter()) {
            OperationPermission op = context.getOperationMap().get(perm.getOperationPermissionId());
            if (op == null || op.getCode() == null || !context.getOperationCodesFilter().contains(op.getCode())) {
                return false;
            }
        }

        ResourceEntity resource = perm.getResourceEntityId() == null
            ? null : context.getResourceMap().get(perm.getResourceEntityId());

        // Must have resource if not scopeAll
        if (resource == null && !Boolean.TRUE.equals(perm.getScopeAll())) {
            return false;
        }

        if (resource != null) {
            // Filter by resource type codes
            String resourceTypeCode = context.getResourceTypeCodeMap().get(resource.getResourceType());
            if (context.hasResourceTypeCodesFilter()
                && (resourceTypeCode == null || !context.getRequest().resourceTypeCodes().contains(resourceTypeCode))) {
                return false;
            }

            // Filter by resource keyword
            if (context.hasResourceKeywordFilter()
                && (resource.getName() == null || !resource.getName().contains(context.getRequest().resourceKeyword()))) {
                return false;
            }

            // Filter by API resources
            if (!context.shouldIncludeApiResources() && context.getApiTypeValue() != null
                && Objects.equals(context.getApiTypeValue(), resource.getResourceType())) {
                return false;
            }

            // Filter by domain
            if (context.hasDomainFilter()) {
                if (context.getDomainId() == null
                    || (!Objects.equals(context.getDomainId(), resource.getBizDomainId()) && resource.getBizDomainId() != null)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Build a ResourcePermissionView for a single resource.
     */
    private ResourcePermissionView buildResourcePermissionView(
            Long resourceId,
            List<RoleResourcePermission> perms,
            ResourceEntity resource,
            PermissionQueryContext context,
            int sourceRoleLimit,
            boolean includeSourceRoles) {

        Set<String> operationCodes = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .map(context.getOperationMap()::get)
            .filter(Objects::nonNull)
            .map(OperationPermission::getCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> matchedPermissionIds = perms.stream()
            .map(RoleResourcePermission::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SourceRoleView> sourceRoles = perms.stream()
            .map(RoleResourcePermission::getAbstractRoleId)
            .distinct()
            .map(context.getRoleMap()::get)
            .filter(Objects::nonNull)
            .map(role -> new SourceRoleView(
                context.getRoleTypeCodeMap().get(role.getRoleType()),
                role.getExternalId(),
                role.getName(),
                List.of()
            ))
            .toList();

        int sourceRoleCount = sourceRoles.size();
        boolean sourceRolesTruncated = includeSourceRoles && sourceRoleLimit > 0 && sourceRoleCount > sourceRoleLimit;
        List<SourceRoleView> returnedSourceRoles = includeSourceRoles
            ? (sourceRoleLimit > 0 ? sourceRoles.stream().limit(sourceRoleLimit).toList() : List.of())
            : List.of();

        return new ResourcePermissionView(
            resourceId,
            context.getDomainCodeMap().get(resource.getBizDomainId()),
            resource.getCode(),
            resource.getName(),
            context.getResourceTypeCodeMap().get(resource.getResourceType()),
            resource.getCodeType(),
            perms.stream().anyMatch(p -> Boolean.TRUE.equals(p.getScopeAll())),
            new ArrayList<>(operationCodes),
            returnedSourceRoles,
            sourceRoleCount,
            sourceRolesTruncated,
            new ArrayList<>(matchedPermissionIds)
        );
    }

    /**
     * Create an empty response for early exit scenarios.
     */
    private PaginatedResp<ResourcePermissionView> emptyResponse(PermissionQueryContext context) {
        return new PaginatedResp<>(List.of(), 0, context.getPageNum(), context.getPageSize(), false);
    }

    private Map<Long, AbstractRole> loadRoles(Long tenantId, Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.ID.in(roleIds))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(AbstractRole::getId, role -> role));
    }

    private Map<Long, OperationPermission> loadOperations(Set<Long> operationIds) {
        if (operationIds.isEmpty()) {
            return Map.of();
        }
        return operationPermissionMapper.selectListByQuery(
            QueryWrapper.create().where(OPERATION_PERMISSION.ID.in(operationIds))
        ).stream().collect(Collectors.toMap(OperationPermission::getId, op -> op));
    }

    private Map<Long, ResourceEntity> loadResources(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.ID.in(resourceIds))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(ResourceEntity::getId, resource -> resource));
    }

    private Map<Long, String> loadDomainCodes(Long tenantId, Set<Long> domainIds) {
        if (domainIds.isEmpty()) {
            return Map.of();
        }
        return bizDomainMapper.selectListByQuery(
            QueryWrapper.create()
                .where(BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BIZ_DOMAIN.ID.in(domainIds))
                .and(BIZ_DOMAIN.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(BizDomain::getId, BizDomain::getCode));
    }

    private Set<Long> filterRoleIds(Long tenantId, Set<Long> roleIds, String sourceRoleExternalId, String roleTypeCode, String domainCode) {
        if ((sourceRoleExternalId == null || sourceRoleExternalId.isBlank()) && (roleTypeCode == null || roleTypeCode.isBlank())) {
            return roleIds;
        }
        Integer roleTypeValue = roleTypeCode == null || roleTypeCode.isBlank()
            ? null : typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
        Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.ID.in(roleIds))
                .and(sourceRoleExternalId == null || sourceRoleExternalId.isBlank()
                    ? ABSTRACT_ROLE.ID.isNotNull()
                    : ABSTRACT_ROLE.EXTERNAL_ID.eq(sourceRoleExternalId))
                .and(roleTypeValue == null ? ABSTRACT_ROLE.ID.isNotNull() : ABSTRACT_ROLE.ROLE_TYPE.eq(roleTypeValue))
                .and(domainCode == null ? ABSTRACT_ROLE.ID.isNotNull()
                    : ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId).or(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        ).stream().map(AbstractRole::getId).collect(Collectors.toSet());
    }

    @Override
    public ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType) {
        Long resourceEntityId = typeResolutionService.resolveResourceId(tenantId, resourceTypeCode, resourceCode, codeType, domainCode);
        if (resourceEntityId == null) {
            return null;
        }
        ResourceEntity resource = resourceEntityMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.ID.eq(resourceEntityId))
                .and(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        if (resource == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Map<Long, List<RoleResourcePermission>> byRole = perms.stream()
            .collect(Collectors.groupingBy(RoleResourcePermission::getAbstractRoleId));

        // Batch load roles to avoid N+1 queries
        Map<Long, AbstractRole> roleMap = loadRoles(tenantId, byRole.keySet());

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> opIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = loadOperations(opIds);

        // Batch resolve role type codes (avoid N+1)
        Set<Integer> roleTypeValues = roleMap.values().stream()
            .map(AbstractRole::getRoleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> roleTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "role_type", roleTypeValues);

        List<RoleGrantInfo> roleInfos = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : byRole.entrySet()) {
            AbstractRole role = roleMap.get(entry.getKey());
            List<String> opCodes = entry.getValue().stream()
                .map(p -> {
                    OperationPermission op = opMap.get(p.getOperationPermissionId());
                    return op != null ? op.getCode() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            roleInfos.add(new RoleGrantInfo(
                entry.getKey(),
                role != null ? role.getName() : null,
                role != null ? roleTypeCodeMap.get(role.getRoleType()) : null,
                opCodes,
                entry.getValue().get(0).getGrantSource()
            ));
        }

        return new ResourcePermissionViewResp(
            resourceEntityId, resource.getCode(), resource.getName(), roleInfos
        );
    }

    @Override
    public RolePermissionViewResp getRolePermissions(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, boolean expandSub) {
        Long roleId = typeResolutionService.resolveRoleId(tenantId, roleTypeCode, roleExternalId, domainCode);
        if (roleId == null) {
            return null;
        }
        AbstractRole role = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(roleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (role == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Batch load resource entities to avoid N+1 queries
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(tenantId, resourceIds);

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> opIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = loadOperations(opIds);

        // Batch resolve resource type codes (avoid N+1)
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        List<PermissionItem> items = perms.stream()
            .map(p -> {
                ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
                OperationPermission op = opMap.get(p.getOperationPermissionId());
                return new PermissionItem(
                    p.getId(),
                    p.getResourceEntityId(),
                    resource != null ? resource.getCode() : null,
                    resource != null ? resource.getName() : null,
                    resourceTypeCodeMap.get(p.getResourceType()),
                    p.getOperationPermissionId(),
                    op != null ? op.getCode() : null,
                    op != null ? op.getName() : null,
                    p.getDependOn(),
                    p.getConditionId(),
                    p.getCanGrant(),
                    p.getGrantSource()
                );
            })
            .collect(Collectors.toList());

        return new RolePermissionViewResp(roleId, role.getName(), typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()), items);
    }

    @Override
    public PaginatedResp<PermissionItem> getRolePermissionItemsPaged(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, int pageNum, int pageSize) {
        Long roleId = typeResolutionService.resolveRoleId(tenantId, roleTypeCode, roleExternalId, domainCode);
        if (roleId == null) {
            return new PaginatedResp<>(List.of(), 0L, pageNum, pageSize, false);
        }
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        QueryWrapper base = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        long total = rolePermMapper.selectCountByQuery(base);
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(base.clone().limit(pageSize).offset(offset));
        // Batch load resource entities to avoid N+1 queries
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(tenantId, resourceIds);

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> opIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = loadOperations(opIds);

        // Batch resolve resource type codes (avoid N+1)
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        List<PermissionItem> items = perms.stream().map(p -> {
            ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
            OperationPermission op = opMap.get(p.getOperationPermissionId());
            return new PermissionItem(
                p.getId(),
                p.getResourceEntityId(),
                resource != null ? resource.getCode() : null,
                resource != null ? resource.getName() : null,
                resourceTypeCodeMap.get(p.getResourceType()),
                p.getOperationPermissionId(),
                op != null ? op.getCode() : null,
                op != null ? op.getName() : null,
                p.getDependOn(),
                p.getConditionId(),
                p.getCanGrant(),
                p.getGrantSource()
            );
        }).toList();
        return new PaginatedResp<>(items, total, pageNum, pageSize, offset + items.size() < total);
    }

    @Override
    public PermissionExplainResp explain(Long tenantId, PermissionExplainReq req) {
        AuthCheckResp checkResp;
        if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType())) {
            checkResp = checkRoleDirectGrant(tenantId, req);
        } else {
            checkResp = permissionService.check(
                tenantId,
                new AuthCheckReq(
                    req.subjectTypeCode(),
                    req.subjectExternalId(),
                    req.resourceTypeCode(),
                    req.resourceCode(),
                    req.operationCode(),
                    req.domainCode(),
                    req.codeType(),
                    "NONE",
                    Map.of()
                )
            );
        }

        List<PermissionExplainResp.SourceRole> sourceRoles = List.of();
        if (Boolean.TRUE.equals(req.includeSourceRoles())) {
            if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType())) {
                Long roleId = typeResolutionService.resolveRoleId(
                    tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
                if (roleId != null) {
                    AbstractRole r = abstractRoleMapper.selectOneByQuery(
                        QueryWrapper.create()
                            .where(ABSTRACT_ROLE.ID.eq(roleId))
                            .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                            .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
                    );
                    if (r != null) {
                        sourceRoles = List.of(new PermissionExplainResp.SourceRole(
                            typeResolutionService.resolveTypeCode(tenantId, "role_type", r.getRoleType()),
                            r.getExternalId(),
                            r.getName(),
                            List.of()
                        ));
                    }
                }
            } else if (checkResp.matchedRoleIds() != null && !checkResp.matchedRoleIds().isEmpty()) {
                sourceRoles = abstractRoleMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                        .and(ABSTRACT_ROLE.ID.in(checkResp.matchedRoleIds()))
                        .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
                ).stream().map(role -> new PermissionExplainResp.SourceRole(
                    typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()),
                    role.getExternalId(),
                    role.getName(),
                    List.of()
                )).toList();
            }
        }

        List<RecentChangeResp> recentChanges = List.of();
        if (Boolean.TRUE.equals(req.includeRecentChanges())) {
            int recentDays = req.recentDays() == null ? 30 : Math.max(req.recentDays(), 1);
            PermissionRecentChangesReq recentReq = new PermissionRecentChangesReq(
                req.targetType(), req.subjectTypeCode(), req.subjectExternalId(),
                req.roleTypeCode(), req.roleExternalId(), req.domainCode(),
                LocalDateTime.now().minusDays(recentDays), LocalDateTime.now(),
                null, 1, 50
            );
            recentChanges = recentChanges(tenantId, recentReq).items();
        }

        return new PermissionExplainResp(
            req.targetType(),
            checkResp.allowed(),
            checkResp.reason(),
            new PermissionExplainResp.PermissionKey(
                req.domainCode(), req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.operationCode(), false
            ),
            sourceRoles,
            checkResp.matchedPermissionIds(),
            recentChanges
        );
    }

    @Override
    public PermissionRecentChangesResp recentChanges(Long tenantId, PermissionRecentChangesReq req) {
        Long userId = null;
        Long roleId = null;
        if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType()) && req.subjectTypeCode() != null && req.subjectExternalId() != null) {
            userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        } else if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType()) && req.roleTypeCode() != null && req.roleExternalId() != null) {
            roleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        }
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType()) && userId == null) {
            return new PermissionRecentChangesResp(List.of(), 0, pageNum, pageSize, false);
        }
        if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType()) && roleId == null) {
            return new PermissionRecentChangesResp(List.of(), 0, pageNum, pageSize, false);
        }
        long total = logQueryService.countChangeLogsFiltered(
            tenantId, userId, roleId, req.since(), req.until(), req.eventTypes());
        List<ChangeLogResp> logs = logQueryService.listChangeLogsFiltered(
            tenantId, userId, roleId, req.since(), req.until(), req.eventTypes(), offset, pageSize);
        List<RecentChangeResp> items = logs.stream().map(this::toRecentChange).toList();
        int totalInt = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        return new PermissionRecentChangesResp(
            items, totalInt, pageNum, pageSize, offset + items.size() < total);
    }

    @Override
    public ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new ItemsResp<>(List.of());
        }
        UserPermissionViewReq viewReq = new UserPermissionViewReq(
            PermConstants.TargetType.USER, req.subjectTypeCode(), req.subjectExternalId(), req.domainCode(),
            null, null, null, null, null, null,
            false, false, true, 200, 1, 200
        );
        PaginatedResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, viewReq);
        List<EffectiveRoleResp> roles = paged.items().stream()
            .flatMap(item -> item.sourceRoles().stream())
            .map(sourceRole -> new EffectiveRoleResp(
                sourceRole.roleTypeCode(), sourceRole.roleExternalId(), sourceRole.roleName()))
            .collect(Collectors.collectingAndThen(
                Collectors.toCollection(LinkedHashSet::new),
                ArrayList::new
            ));
        return new ItemsResp<>(roles);
    }

    private AuthCheckResp checkRoleDirectGrant(Long tenantId, PermissionExplainReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            return AuthCheckResp.deny("ROLE_NOT_FOUND");
        }
        AbstractRole role = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(roleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (role == null || role.getStatus() == null || role.getStatus() != PermissionConstants.ENABLED_STATUS) {
            return AuthCheckResp.deny("ROLE_NOT_FOUND");
        }

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

        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        OperationPermission targetOp = operationPermissionMapper.selectOneById(operationPermissionId);
        if (targetOp == null || targetOp.getBinaryBit() == null || targetOp.getBinaryBit() == 0L) {
            return AuthCheckResp.deny("NO_PERMISSION");
        }

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> grantedOpIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = loadOperations(grantedOpIds);

        List<Long> matchedPermissionIds = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = opMap.get(perm.getOperationPermissionId());
            if (grantedOp == null) {
                continue;
            }
            if (grantedOp.matchesBit(targetOp)) {
                matchedPermissionIds.add(perm.getId());
            }
        }
        if (matchedPermissionIds.isEmpty()) {
            return AuthCheckResp.deny("NO_PERMISSION");
        }
        return AuthCheckResp.allow(List.of(roleId), matchedPermissionIds, false);
    }

    private RecentChangeResp toRecentChange(ChangeLogResp log) {
        return new RecentChangeResp(
            log.id(),
            parseText(log.diffSnapshot(), "eventType"),
            parseText(log.diffSnapshot(), "items[0].changeType"),
            "POSSIBLE",
            parseText(log.diffSnapshot(), "items[0].message"),
            new RecentChangeResp.PermissionKey(
                parseText(log.diffSnapshot(), "items[0].permission.domainCode"),
                parseText(log.diffSnapshot(), "items[0].permission.resourceTypeCode"),
                parseText(log.diffSnapshot(), "items[0].permission.resourceCode"),
                parseText(log.diffSnapshot(), "items[0].permission.codeType"),
                parseText(log.diffSnapshot(), "items[0].permission.operationCode"),
                parseBoolean(log.diffSnapshot(), "items[0].permission.scopeAll")
            ),
            new RecentChangeResp.SourceRole(
                parseText(log.diffSnapshot(), "items[0].role.roleTypeCode"),
                parseText(log.diffSnapshot(), "items[0].role.roleExternalId"),
                parseText(log.diffSnapshot(), "items[0].role.roleName")
            ),
            null,
            null,
            log.changeReason(),
            log.createdAt()
        );
    }

    private String parseText(String json, String path) {
        JsonNode node = parsePath(json, path);
        return node == null || node.isNull() ? null : node.asText();
    }

    private Boolean parseBoolean(String json, String path) {
        JsonNode node = parsePath(json, path);
        return node == null || node.isNull() ? null : node.asBoolean();
    }

    private JsonNode parsePath(String json, String path) {
        if (json == null || json.isBlank() || path == null || path.isBlank()) {
            return null;
        }
        try {
            JsonNode current = objectMapper.readTree(json);
            String[] segments = path.split("\\.");
            for (String segment : segments) {
                if (segment.endsWith("]") && segment.contains("[")) {
                    String field = segment.substring(0, segment.indexOf('['));
                    int idx = Integer.parseInt(segment.substring(segment.indexOf('[') + 1, segment.length() - 1));
                    current = current.path(field);
                    if (!current.isArray() || current.size() <= idx) {
                        return null;
                    }
                    current = current.get(idx);
                } else {
                    current = current.path(segment);
                }
                if (current.isMissingNode()) {
                    return null;
                }
            }
            return current;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<ResourcePermissionTreeResp> getUserResourceTree(Long tenantId, Long userId, UserResourceTreeReq req) {
        UserPermissionViewReq treeReq = new UserPermissionViewReq(
            PermConstants.TargetType.USER, req.subjectTypeCode(), req.subjectExternalId(), req.domainCode(),
            null, null, req.resourceTypeCodes(), req.operationCodes(),
            req.resourceKeyword(), null, false, false, false, null, 1, 10000
        );
        PaginatedResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, treeReq);
        Set<Long> permittedIds = paged.items().stream()
            .map(ResourcePermissionView::resourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (permittedIds.isEmpty()) {
            return List.of();
        }

        List<ResourceEntity> entities = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .where(RESOURCE_ENTITY.ID.in(permittedIds))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );

        Map<Long, ResourcePermissionView> viewMap = paged.items().stream()
            .filter(v -> v.resourceEntityId() != null)
            .collect(Collectors.toMap(ResourcePermissionView::resourceEntityId, v -> v, (a, b) -> a));

        Map<Long, ResourceEntity> entityMap = entities.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, e -> e, (a, b) -> a));

        Map<Long, List<Long>> childrenMap = new HashMap<>();
        Set<Long> childIds = new HashSet<>();
        for (ResourceEntity entity : entities) {
            if (entity.getParentId() != null && permittedIds.contains(entity.getParentId())) {
                childrenMap.computeIfAbsent(entity.getParentId(), k -> new ArrayList<>()).add(entity.getId());
                childIds.add(entity.getId());
            }
        }

        List<ResourcePermissionTreeResp> roots = new ArrayList<>();
        Set<Long> treeVisited = new HashSet<>();
        for (ResourceEntity entity : entities) {
            if (!childIds.contains(entity.getId())) {
                roots.add(buildPermissionTreeNode(entity.getId(), viewMap, entityMap, childrenMap, tenantId, treeVisited));
            }
        }
        return roots;
    }

    private ResourcePermissionTreeResp buildPermissionTreeNode(
            Long entityId,
            Map<Long, ResourcePermissionView> viewMap,
            Map<Long, ResourceEntity> entityMap,
            Map<Long, List<Long>> childrenMap,
            Long tenantId,
            Set<Long> visited) {
        // 防止循环引用：检查是否已访问
        if (visited.contains(entityId)) {
            return new ResourcePermissionTreeResp(
                entityId, null, null, null, null, PermConstants.CodeType.DEFAULT, false, List.of(), List.of()
            );
        }
        visited.add(entityId);

        ResourcePermissionView view = viewMap.get(entityId);
        ResourceEntity entity = entityMap.get(entityId);
        String domainCode = null;
        String resourceTypeCode = null;
        String codeType = PermConstants.CodeType.DEFAULT;
        List<String> operationCodes = List.of();
        boolean scopeAll = false;
        String resourceCode = null;
        String resourceName = null;

        if (view != null) {
            domainCode = view.domainCode();
            resourceTypeCode = view.resourceTypeCode();
            codeType = view.codeType();
            operationCodes = view.operationCodes();
            scopeAll = view.scopeAll();
            resourceCode = view.resourceCode();
            resourceName = view.resourceName();
        } else if (entity != null) {
            resourceCode = entity.getCode();
            resourceName = entity.getName();
            resourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", entity.getResourceType());
        }

        List<Long> childEntityIds = childrenMap.getOrDefault(entityId, List.of());
        List<ResourcePermissionTreeResp> children = childEntityIds.stream()
            .map(childId -> buildPermissionTreeNode(childId, viewMap, entityMap, childrenMap, tenantId, visited))
            .collect(Collectors.toList());

        return new ResourcePermissionTreeResp(
            entityId, domainCode, resourceCode, resourceName,
            resourceTypeCode, codeType, scopeAll, operationCodes, children
        );
    }
}
