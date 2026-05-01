package cn.ac.fage.accessmesh.permission.service.impl;

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
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
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
    private final AdvancedFeatureService advancedFeatureService;
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
                                     AdvancedFeatureService advancedFeatureService,
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
        this.advancedFeatureService = advancedFeatureService;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserPermissionViewResp getUserPermissions(Long tenantId, Long userId) {
        PaginatedResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, new UserPermissionViewReq(
            "USER", null, null, null, null, null, null, null, null, null,
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
            subjectTypeCode = "USER";
        }
        return new UserPermissionViewResp(subjectTypeCode, user.getExternalId(), user.getName(), paged.items());
    }

    @Override
    public PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req) {
        int pageNum = req.pageNum() == null ? 1 : req.pageNum();
        int pageSize = Math.min(req.pageSize() == null ? 50 : req.pageSize(), 200);
        if ("USER".equalsIgnoreCase(req.targetType())) {
            Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
            if (userId == null) {
                return new PermissionEffectivePermissionsResp("USER", List.of(), 0, pageNum, pageSize, false);
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
            return new PermissionEffectivePermissionsResp("USER", items, (int) paged.total(), pageNum, pageSize, paged.hasNext());
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
        return new PermissionEffectivePermissionsResp("ROLE", items, (int) paged.total(), pageNum, pageSize, paged.hasNext());
    }

    PaginatedResp<ResourcePermissionView> getUserPermissionsWithFilters(Long tenantId, Long userId, UserPermissionViewReq req) {
        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.ID.eq(userId))
                .and(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        int pageNum = req.pageNum() == null ? 1 : req.pageNum();
        int pageSize = req.pageSize() == null ? 50 : Math.min(req.pageSize(), 200);
        if (user == null) {
            return new PaginatedResp<>(List.of(), 0, pageNum, pageSize, false);
        }

        Long domainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, domainId);
        if (roleIds.isEmpty()) {
            return new PaginatedResp<>(Collections.emptyList(), 0, pageNum, pageSize, false);
        }
        int offset = Math.max((pageNum - 1) * pageSize, 0);

        Set<Long> filteredRoleIds = filterRoleIds(tenantId, roleIds, req.sourceRoleExternalId(), req.roleTypeCode(), req.domainCode());
        if (filteredRoleIds.isEmpty()) {
            return new PaginatedResp<>(List.of(), 0, pageNum, pageSize, false);
        }
        Map<Long, AbstractRole> roleMap = loadRoles(tenantId, filteredRoleIds);

        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(filteredRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Set<Long> operationIds = allPerms.stream().map(RoleResourcePermission::getOperationPermissionId).collect(Collectors.toSet());
        Map<Long, OperationPermission> operationMap = loadOperations(operationIds);
        Set<Long> resourceIds = allPerms.stream().map(RoleResourcePermission::getResourceEntityId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(tenantId, resourceIds);
        Set<Long> domainIds = resourceMap.values().stream().map(ResourceEntity::getBizDomainId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> domainCodeMap = loadDomainCodes(tenantId, domainIds);
        Integer apiTypeValue = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "API");

        List<RoleResourcePermission> filteredPerms = allPerms.stream().filter(p -> {
            if (Boolean.FALSE.equals(req.includeScopes()) && p.getDependOn() != null) {
                return false;
            }
            if (req.operationCodes() != null && !req.operationCodes().isEmpty()) {
                OperationPermission op = operationMap.get(p.getOperationPermissionId());
                if (op == null || op.getCode() == null || !req.operationCodes().contains(op.getCode())) {
                    return false;
                }
            }
            ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
            if (resource == null && !Boolean.TRUE.equals(p.getScopeAll())) {
                return false;
            }
            if (resource != null) {
                String resourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", resource.getResourceType());
                if (req.resourceTypeCodes() != null && !req.resourceTypeCodes().isEmpty()
                    && (resourceTypeCode == null || !req.resourceTypeCodes().contains(resourceTypeCode))) {
                    return false;
                }
                if (req.resourceKeyword() != null && !req.resourceKeyword().isBlank()
                    && (resource.getName() == null || !resource.getName().contains(req.resourceKeyword()))) {
                    return false;
                }
                if (Boolean.FALSE.equals(req.includeApiResources()) && apiTypeValue != null
                    && Objects.equals(apiTypeValue, resource.getResourceType())) {
                    return false;
                }
                if (req.domainCode() != null) {
                    if (domainId == null || (!Objects.equals(domainId, resource.getBizDomainId()) && resource.getBizDomainId() != null)) {
                        return false;
                    }
                }
            }
            return true;
        }).toList();

        long total = filteredPerms.size();
        List<RoleResourcePermission> pagePerms = filteredPerms.stream().skip(offset).limit(pageSize).toList();
        Map<Long, List<RoleResourcePermission>> byResource = pagePerms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));

        int sourceRoleLimit = req.sourceRoleLimit() == null ? 20 : Math.max(req.sourceRoleLimit(), 0);
        boolean includeSourceRoles = req.includeSourceRoles() == null || req.includeSourceRoles();
        List<ResourcePermissionView> resourceViews = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : byResource.entrySet()) {
            ResourceEntity resource = resourceMap.get(entry.getKey());
            if (resource == null) {
                continue;
            }
            Set<String> operationCodes = entry.getValue().stream()
                .map(RoleResourcePermission::getOperationPermissionId)
                .map(operationMap::get)
                .filter(Objects::nonNull)
                .map(OperationPermission::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> matchedPermissionIds = entry.getValue().stream()
                .map(RoleResourcePermission::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            List<SourceRoleView> sourceRoles = entry.getValue().stream()
                .map(RoleResourcePermission::getAbstractRoleId)
                .distinct()
                .map(roleMap::get)
                .filter(Objects::nonNull)
                .map(role -> new SourceRoleView(
                    typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()),
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

            resourceViews.add(new ResourcePermissionView(
                resource.getId(),
                domainCodeMap.get(resource.getBizDomainId()),
                resource.getCode(),
                resource.getName(),
                typeResolutionService.resolveTypeCode(tenantId, "resource_type", resource.getResourceType()),
                resource.getCodeType(),
                entry.getValue().stream().anyMatch(p -> Boolean.TRUE.equals(p.getScopeAll())),
                new ArrayList<>(operationCodes),
                returnedSourceRoles,
                sourceRoleCount,
                sourceRolesTruncated,
                new ArrayList<>(matchedPermissionIds)
            ));
        }

        return new PaginatedResp<>(resourceViews, total, pageNum, pageSize, offset + pageSize < total);
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

        List<RoleGrantInfo> roleInfos = new ArrayList<>();
        for (Map.Entry<Long, List<RoleResourcePermission>> entry : byRole.entrySet()) {
            AbstractRole role = abstractRoleMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(ABSTRACT_ROLE.ID.eq(entry.getKey()))
                    .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            );
            List<String> opCodes = entry.getValue().stream()
                .map(p -> {
                    OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
                    return op != null ? op.getCode() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            roleInfos.add(new RoleGrantInfo(
                entry.getKey(),
                role != null ? role.getName() : null,
                role != null ? typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()) : null,
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

        List<PermissionItem> items = perms.stream()
            .map(p -> {
                ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceEntityMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_ENTITY.ID.eq(p.getResourceEntityId()))
                        .and(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                        .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                );
                OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
                return new PermissionItem(
                    p.getId(),
                    p.getResourceEntityId(),
                    resource != null ? resource.getCode() : null,
                    resource != null ? resource.getName() : null,
                    typeResolutionService.resolveTypeCode(tenantId, "resource_type", p.getResourceType()),
                    p.getOperationPermissionId(),
                    op != null ? op.getCode() : null,
                    op != null ? op.getName() : null,
                    p.getDependOn(),
                    p.getConditionId(),
                    p.getCanManage(),
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
        List<PermissionItem> items = perms.stream().map(p -> {
            ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceEntityMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_ENTITY.ID.eq(p.getResourceEntityId()))
                    .and(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            );
            OperationPermission op = operationPermissionMapper.selectOneById(p.getOperationPermissionId());
            return new PermissionItem(
                p.getId(),
                p.getResourceEntityId(),
                resource != null ? resource.getCode() : null,
                resource != null ? resource.getName() : null,
                typeResolutionService.resolveTypeCode(tenantId, "resource_type", p.getResourceType()),
                p.getOperationPermissionId(),
                op != null ? op.getCode() : null,
                op != null ? op.getName() : null,
                p.getDependOn(),
                p.getConditionId(),
                p.getCanManage(),
                p.getGrantSource()
            );
        }).toList();
        return new PaginatedResp<>(items, total, pageNum, pageSize, offset + items.size() < total);
    }

    @Override
    public PermissionExplainResp explain(Long tenantId, PermissionExplainReq req) {
        AuthCheckResp checkResp;
        if ("ROLE".equalsIgnoreCase(req.targetType())) {
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
            if ("ROLE".equalsIgnoreCase(req.targetType())) {
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
        if ("USER".equalsIgnoreCase(req.targetType()) && req.subjectTypeCode() != null && req.subjectExternalId() != null) {
            userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        } else if ("ROLE".equalsIgnoreCase(req.targetType()) && req.roleTypeCode() != null && req.roleExternalId() != null) {
            roleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        }
        int pageNum = req.pageNum() == null ? 1 : req.pageNum();
        int pageSize = req.pageSize() == null ? 20 : Math.min(req.pageSize(), 200);
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        if ("USER".equalsIgnoreCase(req.targetType()) && userId == null) {
            return new PermissionRecentChangesResp(List.of(), 0, pageNum, pageSize, false);
        }
        if ("ROLE".equalsIgnoreCase(req.targetType()) && roleId == null) {
            return new PermissionRecentChangesResp(List.of(), 0, pageNum, pageSize, false);
        }
        long total = advancedFeatureService.countChangeLogsFiltered(
            tenantId, userId, roleId, req.since(), req.until(), req.eventTypes());
        List<ChangeLogResp> logs = advancedFeatureService.listChangeLogsFiltered(
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
            "USER", req.subjectTypeCode(), req.subjectExternalId(), req.domainCode(),
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
        if (role == null || role.getStatus() == null || role.getStatus() != 1) {
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
        if (targetOp == null) {
            return AuthCheckResp.deny("OPERATION_NOT_FOUND");
        }
        long targetBit = targetOp.getBinaryBit() != null ? targetOp.getBinaryBit() : 0L;
        if (targetBit == 0L) {
            return AuthCheckResp.deny("NO_PERMISSION");
        }

        List<Long> matchedPermissionIds = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = operationPermissionMapper.selectOneById(perm.getOperationPermissionId());
            if (grantedOp == null) {
                continue;
            }
            long effectiveBits = (grantedOp.getBinaryBit() != null ? grantedOp.getBinaryBit() : 0L)
                | (grantedOp.getInheritMask() != null ? grantedOp.getInheritMask() : 0L);
            if ((effectiveBits & targetBit) != 0) {
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
            "USER", req.subjectTypeCode(), req.subjectExternalId(), req.domainCode(),
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
        for (ResourceEntity entity : entities) {
            if (!childIds.contains(entity.getId())) {
                roots.add(buildPermissionTreeNode(entity.getId(), viewMap, entityMap, childrenMap, tenantId));
            }
        }
        return roots;
    }

    private ResourcePermissionTreeResp buildPermissionTreeNode(
            Long entityId,
            Map<Long, ResourcePermissionView> viewMap,
            Map<Long, ResourceEntity> entityMap,
            Map<Long, List<Long>> childrenMap,
            Long tenantId) {
        ResourcePermissionView view = viewMap.get(entityId);
        ResourceEntity entity = entityMap.get(entityId);
        String domainCode = null;
        String resourceTypeCode = null;
        String codeType = "default";
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
            .map(childId -> buildPermissionTreeNode(childId, viewMap, entityMap, childrenMap, tenantId))
            .collect(Collectors.toList());

        return new ResourcePermissionTreeResp(
            entityId, domainCode, resourceCode, resourceName,
            resourceTypeCode, codeType, scopeAll, operationCodes, children
        );
    }
}
