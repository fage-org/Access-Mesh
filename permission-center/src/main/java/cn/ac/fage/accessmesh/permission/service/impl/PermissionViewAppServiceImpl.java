package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.query.PermViewFilter;
import cn.ac.fage.accessmesh.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionExplainReq;
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
import cn.ac.fage.accessmesh.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.permission.util.PermViewAssembler;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限视图应用服务实现
 * <p>
 * 提供用户权限视图、角色权限视图、资源权限视图、权限解释等功能。
 * 所有查询均通过 PermQueryEngine 进行权限校验。
 * 用户视图使用 forUserView 查询管线 + PermViewAssembler 过滤分页。
 * recentChanges 已迁移至 LogQueryAppService，不在此服务中。
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class PermissionViewAppServiceImpl implements PermissionViewAppService {

    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final AuditDomainService auditDomainService;
    private final ObjectMapper objectMapper;
    private final PermQueryEngine engine;
    private final PermViewAssembler permViewAssembler;

    public PermissionViewAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                         ResourceEntityMapper resourceEntityMapper,
                                         OperationPermissionMapper operationPermissionMapper,
                                         RoleResourcePermissionMapper rolePermMapper,
                                         SubjectDomainService subjectDomainService,
                                         TypeResolutionService typeResolutionService,
                                         AuditDomainService auditDomainService,
                                         ObjectMapper objectMapper,
                                         PermQueryEngine engine,
                                         PermViewAssembler permViewAssembler) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.rolePermMapper = rolePermMapper;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.auditDomainService = auditDomainService;
        this.objectMapper = objectMapper;
        this.engine = engine;
        this.permViewAssembler = permViewAssembler;
    }

    @Override
    public PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType())) {
            // USER 分支：操作者需要对被查用户有 VIEW 权限
            Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
            if (userId == null) {
                return new PermissionEffectivePermissionsResp(PermConstants.TargetType.USER, List.of(), 0, pageNum, pageSize, false);
            }
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on USER:" + userId);
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
        // ROLE 分支：操作者需要对被查角色有 VIEW 权限
        Long roleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId != null) {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on ROLE:" + roleId);
            }
        } else if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE");
        }
        PaginatedResp<PermissionItem> paged = getRolePermissionItemsPaged(
            tenantId, roleId, pageNum, pageSize);
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
        // 1. 解析用户角色并过滤
        Set<Long> allRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        if (allRoleIds.isEmpty()) {
            int pageNum = PageUtil.pageNum(req.pageNum());
            int pageSize = PageUtil.pageSize(req.pageSize());
            return new PaginatedResp<>(List.of(), 0L, pageNum, pageSize, false);
        }

        Set<Long> filteredRoleIds = filterRoleIds(tenantId, allRoleIds,
            req.sourceRoleExternalId(), req.roleTypeCode(), req.domainCode());
        if (filteredRoleIds.isEmpty()) {
            int pageNum = PageUtil.pageNum(req.pageNum());
            int pageSize = PageUtil.pageSize(req.pageSize());
            return new PaginatedResp<>(List.of(), 0L, pageNum, pageSize, false);
        }

        // 2. 构建 forUserView 查询
        PermQuery query = PermQuery.forUserView(tenantId, userId);
        query.setRoleIds(filteredRoleIds);

        // 3. 调引擎获取全量结果
        PermResult result = engine.query(query);
        if (!result.allowed()) {
            int pageNum = PageUtil.pageNum(req.pageNum());
            int pageSize = PageUtil.pageSize(req.pageSize());
            return new PaginatedResp<>(List.of(), 0L, pageNum, pageSize, false);
        }

        // 4. 构建过滤条件
        PermViewFilter filter = new PermViewFilter();
        filter.setOperationCodes(req.operationCodes() == null ? null : new LinkedHashSet<>(req.operationCodes()));
        filter.setResourceTypes(req.resourceTypeCodes() == null ? null : new LinkedHashSet<>(req.resourceTypeCodes()));
        filter.setResourceKeyword(req.resourceKeyword());
        filter.setDomainCode(req.domainCode());
        filter.setExcludeApiResources(Boolean.FALSE.equals(req.includeApiResources()));
        filter.setIncludeScopePermissions(!Boolean.FALSE.equals(req.includeScopes()));
        filter.setIncludeSourceRoles(req.includeSourceRoles() == null || req.includeSourceRoles());
        filter.setSourceRoleLimit(req.sourceRoleLimit() == null ? 20 : Math.max(req.sourceRoleLimit(), 0));
        filter.setPageNum(PageUtil.pageNum(req.pageNum()));
        filter.setPageSize(PageUtil.pageSize(req.pageSize()));

        // 5. 通过装配器过滤+分页
        PermViewResult viewResult = permViewAssembler.assemble(tenantId, result, filter);

        // 6. 组装响应
        return buildResponseFromView(viewResult, filter, tenantId);
    }

    private PaginatedResp<ResourcePermissionView> buildResponseFromView(PermViewResult viewResult, PermViewFilter filter, Long tenantId) {
        List<ResourcePermissionView> items = new ArrayList<>();

        // scopeAll 条目：按 resourceType 分组，每种类型生成一个视图项
        Map<Integer, List<RolePermEntry>> scopeAllByType = viewResult.getEntries().stream()
            .filter(e -> Boolean.TRUE.equals(e.scopeAll()) && e.resourceEntityId() == null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType, LinkedHashMap::new, Collectors.toList()));
        Map<Integer, String> scopeAllTypeCodeMap = !scopeAllByType.isEmpty()
            ? typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", scopeAllByType.keySet())
            : Map.of();
        for (Map.Entry<Integer, List<RolePermEntry>> entry : scopeAllByType.entrySet()) {
            String typeCode = scopeAllTypeCodeMap.get(entry.getKey());
            items.add(buildScopeAllPermissionView(typeCode, entry.getValue(), viewResult, filter));
        }

        // 实例级条目：按 resourceEntityId 分组
        Map<Long, List<RolePermEntry>> byResource = viewResult.getEntries().stream()
            .filter(e -> e.resourceEntityId() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceEntityId, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<RolePermEntry>> entry : byResource.entrySet()) {
            ResourceEntity resource = viewResult.getResourceMap().get(entry.getKey());
            if (resource == null) {
                continue;
            }
            items.add(buildResourcePermissionView(entry.getKey(), entry.getValue(), resource, viewResult, filter));
        }

        // 聚合后分页：total 是聚合后的视图项数，跳过/截取在 items 上执行
        long totalItems = items.size();
        int pageNum = (int) viewResult.getPageNum();
        int pageSize = viewResult.getPageSize();
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        List<ResourcePermissionView> pagedItems = items.stream()
            .skip(offset)
            .limit(pageSize)
            .collect(Collectors.toList());
        boolean hasNext = offset + pageSize < totalItems;

        return new PaginatedResp<>(pagedItems, totalItems,
            pageNum, pageSize, hasNext);
    }

    private ResourcePermissionView buildScopeAllPermissionView(
            String resourceTypeCode, List<RolePermEntry> entries, PermViewResult viewResult,
            PermViewFilter filter) {
        Set<String> operationCodes = new LinkedHashSet<>();
        for (RolePermEntry e : entries) {
            if (e.grantedBits() == null || e.resourceType() == null) continue;
            OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                viewResult.getOperationMap(), e.resourceType(), e.grantedBits());
            if (op != null && op.getCode() != null) operationCodes.add(op.getCode());
        }
        Set<Long> matchedPermissionIds = entries.stream()
            .map(RolePermEntry::permissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, PermViewResult.RoleInfo> sourceRoleMap = viewResult.getSourceRoleMap();
        List<SourceRoleView> allSourceRoles = entries.stream()
            .map(RolePermEntry::roleId)
            .distinct()
            .map(sourceRoleMap::get)
            .filter(Objects::nonNull)
            .map(ri -> new SourceRoleView(ri.typeCode(), ri.externalId(), ri.name(), List.of()))
            .toList();

        int sourceRoleCount = allSourceRoles.size();
        int sourceRoleLimit = filter.isIncludeSourceRoles() && filter.getSourceRoleLimit() > 0
            ? filter.getSourceRoleLimit() : 0;
        boolean sourceRolesTruncated = filter.isIncludeSourceRoles() && sourceRoleLimit > 0
            && sourceRoleCount > sourceRoleLimit;
        List<SourceRoleView> sourceRoles = filter.isIncludeSourceRoles()
            ? (sourceRoleLimit > 0 ? allSourceRoles.stream().limit(sourceRoleLimit).toList() : allSourceRoles)
            : List.of();

        return new ResourcePermissionView(
            null, null, null, null, resourceTypeCode, null,
            true,
            new ArrayList<>(operationCodes),
            sourceRoles,
            sourceRoleCount,
            sourceRolesTruncated,
            new ArrayList<>(matchedPermissionIds)
        );
    }

    private ResourcePermissionView buildResourcePermissionView(
            Long resourceId,
            List<RolePermEntry> entries,
            ResourceEntity resource,
            PermViewResult viewResult,
            PermViewFilter filter) {

        // 操作码
        Set<String> operationCodes = new LinkedHashSet<>();
        for (RolePermEntry e : entries) {
            if (e.grantedBits() == null || e.resourceType() == null) {
                continue;
            }
            OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                viewResult.getOperationMap(), e.resourceType(), e.grantedBits());
            if (op != null && op.getCode() != null) {
                operationCodes.add(op.getCode());
            }
        }

        // 匹配的权限ID
        Set<Long> matchedPermissionIds = entries.stream()
            .map(RolePermEntry::permissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        // 来源角色
        Map<Long, PermViewResult.RoleInfo> sourceRoleMap = viewResult.getSourceRoleMap();
        List<SourceRoleView> allSourceRoles = entries.stream()
            .map(RolePermEntry::roleId)
            .distinct()
            .map(sourceRoleMap::get)
            .filter(Objects::nonNull)
            .map(ri -> new SourceRoleView(ri.typeCode(), ri.externalId(), ri.name(), List.of()))
            .toList();

        int sourceRoleCount = allSourceRoles.size();
        int sourceRoleLimit = filter.isIncludeSourceRoles() && filter.getSourceRoleLimit() > 0
            ? filter.getSourceRoleLimit() : 0;
        boolean sourceRolesTruncated = filter.isIncludeSourceRoles() && sourceRoleLimit > 0
            && sourceRoleCount > sourceRoleLimit;
        List<SourceRoleView> returnedSourceRoles = filter.isIncludeSourceRoles()
            ? (sourceRoleLimit > 0 ? allSourceRoles.stream().limit(sourceRoleLimit).toList() : allSourceRoles)
            : List.of();

        // scopeAll
        boolean scopeAll = entries.stream().anyMatch(e -> Boolean.TRUE.equals(e.scopeAll()));

        return new ResourcePermissionView(
            resourceId,
            viewResult.getDomainCodeMap().get(resourceId),
            resource.getCode(),
            resource.getName(),
            viewResult.getResourceMap().get(resourceId) != null && viewResult.getResourceTypeCodeMap() != null
                ? viewResult.getResourceTypeCodeMap().get(resourceId) : null,
            resource.getCodeType(),
            scopeAll,
            new ArrayList<>(operationCodes),
            returnedSourceRoles,
            sourceRoleCount,
            sourceRolesTruncated,
            new ArrayList<>(matchedPermissionIds)
        );
    }

    /**
     * 按来源角色和角色类型过滤角色ID
     */
    private Set<Long> filterRoleIds(Long tenantId, Set<Long> roleIds,
                                     String sourceRoleExternalId, String roleTypeCode, String domainCode) {
        if ((sourceRoleExternalId == null || sourceRoleExternalId.isBlank())
            && (roleTypeCode == null || roleTypeCode.isBlank())) {
            return roleIds;
        }
        Integer roleTypeValue = roleTypeCode == null || roleTypeCode.isBlank()
            ? null : typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
        return abstractRoleMapper.selectFilteredByIds(tenantId, roleIds,
            sourceRoleExternalId, roleTypeValue).stream()
            .map(AbstractRole::getId).collect(Collectors.toSet());
    }

    @Override
    public ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType) {
        Long operatorId = OperatorContext.getOperatorId();
        Long resourceEntityId = typeResolutionService.resolveResourceId(tenantId, resourceTypeCode, resourceCode, codeType, domainCode);
        if (resourceEntityId != null) {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceEntityId, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on RESOURCE:" + resourceEntityId);
            }
        } else {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on RESOURCE");
            }
        }

        if (resourceEntityId == null) {
            return null;
        }
        ResourceEntity resource = resourceEntityMapper.selectValidById(tenantId, resourceEntityId);
        if (resource == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByResourceEntityId(
            tenantId, resourceEntityId);

        Map<Long, List<RoleResourcePermission>> byRole = perms.stream()
            .collect(Collectors.groupingBy(RoleResourcePermission::getAbstractRoleId));

        Map<Long, AbstractRole> roleMap = abstractRoleMapper.selectValidByIds(tenantId, byRole.keySet())
            .stream().collect(Collectors.toMap(AbstractRole::getId, role -> role));

        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = new LinkedHashMap<>();
        for (Integer rt : resourceTypeValues) {
            if (rt == null) continue;
            for (OperationPermission op : operationPermissionMapper.selectByTenantAndResourceType(tenantId, rt)) {
                opMap.put(op.getId(), op);
            }
        }

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
                    OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, p.getResourceType(), p.getGrantedBits());
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
        Long operatorId = OperatorContext.getOperatorId();
        Long roleId = typeResolutionService.resolveRoleId(tenantId, roleTypeCode, roleExternalId, domainCode);
        if (roleId != null) {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on ROLE:" + roleId);
            }
        } else {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on ROLE");
            }
        }

        if (roleId == null) {
            return null;
        }
        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        if (role == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleId(tenantId, roleId);

        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = resourceEntityMapper.selectValidByIds(tenantId, resourceIds)
            .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));

        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);
        Map<Long, OperationPermission> opMap = new LinkedHashMap<>();
        for (Integer rt : resourceTypeValues) {
            if (rt == null) continue;
            for (OperationPermission op : operationPermissionMapper.selectByTenantAndResourceType(tenantId, rt)) {
                opMap.put(op.getId(), op);
            }
        }

        List<PermissionItem> items = perms.stream()
            .map(p -> {
                ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
                OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, p.getResourceType(), p.getGrantedBits());
                return new PermissionItem(
                    p.getId(),
                    p.getResourceEntityId(),
                    resource != null ? resource.getCode() : null,
                    resource != null ? resource.getName() : null,
                    resourceTypeCodeMap.get(p.getResourceType()),
                    p.getGrantedBits(),
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

    private PaginatedResp<PermissionItem> getRolePermissionItemsPaged(Long tenantId, Long roleId, int pageNum, int pageSize) {
        if (roleId == null) {
            return new PaginatedResp<>(List.of(), 0L, pageNum, pageSize, false);
        }
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        long total = rolePermMapper.countByRoleId(tenantId, roleId);
        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleId(tenantId, roleId);
        List<RoleResourcePermission> paged = perms.stream().skip(offset).limit(pageSize).toList();
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = resourceEntityMapper.selectValidByIds(tenantId, resourceIds)
            .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));

        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);
        Map<Long, OperationPermission> opMap = resourceTypeValues.isEmpty() ? Map.of()
            : operationPermissionMapper.selectByTenantAndResourceTypes(tenantId, resourceTypeValues)
                .stream().collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));

        List<PermissionItem> items = paged.stream().map(p -> {
            ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
            OperationPermission op = OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, p.getResourceType(), p.getGrantedBits());
            return new PermissionItem(
                p.getId(),
                p.getResourceEntityId(),
                resource != null ? resource.getCode() : null,
                resource != null ? resource.getName() : null,
                resourceTypeCodeMap.get(p.getResourceType()),
                p.getGrantedBits(),
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
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SYSTEM_CONFIG");
        }

        // 预先解析 roleId（ROLE 目标），避免在权限检查和 includeSourceRoles 中重复解析
        Long targetRoleId = PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType())
            ? typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode())
            : null;

        AuthCheckResp checkResp;
        if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType())) {
            if (targetRoleId == null) {
                checkResp = AuthCheckResp.deny("ROLE_NOT_FOUND");
            } else {
                PermQuery q = PermQuery.forAuthCheck(tenantId, null,
                    req.resourceTypeCode(), req.resourceCode(), req.operationCode());
                q.setRoleIds(Set.of(targetRoleId));
                q.setCodeType(req.codeType());
                q.setContext(Map.of());
                checkResp = PermResultUtils.toAuthCheckResp(engine.query(q));
            }
        } else {
            // USER 分支：直接调引擎，与 ROLE 分支保持一致
            Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
            if (userId == null) {
                checkResp = AuthCheckResp.deny("USER_NOT_FOUND");
            } else {
                PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                    req.resourceTypeCode(), req.resourceCode(), req.operationCode());
                q.setCodeType(req.codeType());
                q.setContext(Map.of());
                checkResp = PermResultUtils.toAuthCheckResp(engine.query(q));
            }
        }

        List<PermissionExplainResp.SourceRole> sourceRoles = List.of();
        if (Boolean.TRUE.equals(req.includeSourceRoles())) {
            if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType())) {
                if (targetRoleId != null) {
                    AbstractRole r = abstractRoleMapper.selectValidById(targetRoleId, tenantId);
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
                sourceRoles = abstractRoleMapper.selectValidByIds(tenantId, new java.util.HashSet<>(checkResp.matchedRoleIds())).stream()
                    .map(role -> new PermissionExplainResp.SourceRole(
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
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime since = now.minusDays(recentDays);

            Long changeUserId = null;
            Long changeRoleId = null;
            if (PermConstants.TargetType.USER.equalsIgnoreCase(req.targetType()) && req.subjectTypeCode() != null && req.subjectExternalId() != null) {
                changeUserId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
            } else if (PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType()) && req.roleTypeCode() != null && req.roleExternalId() != null) {
                changeRoleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
            }
            if (changeUserId != null || changeRoleId != null) {
                List<PermissionChangeLog> recentLogs = auditDomainService.queryRecentChanges(
                    tenantId, changeUserId, changeRoleId, since, now, null, 0, 50);
                recentChanges = recentLogs.stream()
                    .map(this::toRecentChange)
                    .toList();
            }
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
    public ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId != null) {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on USER:" + userId);
            }
        } else {
            if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on USER");
            }
        }

        if (userId == null) {
            return new ItemsResp<>(List.of());
        }
        // 直接获取用户有效角色，避免通过分页查询间接提取可能截断角色列表
        Set<Long> effectiveRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        List<EffectiveRoleResp> roles = abstractRoleMapper.selectValidByIds(tenantId, effectiveRoleIds).stream()
            .map(role -> new EffectiveRoleResp(
                typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()),
                role.getExternalId(), role.getName()))
            .collect(Collectors.collectingAndThen(
                Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        return new ItemsResp<>(roles);
    }

    // ===== 私有辅助方法 =====

    private RecentChangeResp toRecentChange(PermissionChangeLog log) {
        return new RecentChangeResp(
            log.getId(),
            parseText(log.getDiffSnapshot(), "eventType"),
            parseText(log.getDiffSnapshot(), "items[0].changeType"),
            "POSSIBLE",
            parseText(log.getDiffSnapshot(), "items[0].message"),
            new RecentChangeResp.PermissionKey(
                parseText(log.getDiffSnapshot(), "items[0].permission.domainCode"),
                parseText(log.getDiffSnapshot(), "items[0].permission.resourceTypeCode"),
                parseText(log.getDiffSnapshot(), "items[0].permission.resourceCode"),
                parseText(log.getDiffSnapshot(), "items[0].permission.codeType"),
                parseText(log.getDiffSnapshot(), "items[0].permission.operationCode"),
                parseBoolean(log.getDiffSnapshot(), "items[0].permission.scopeAll")
            ),
            new RecentChangeResp.SourceRole(
                parseText(log.getDiffSnapshot(), "items[0].role.roleTypeCode"),
                parseText(log.getDiffSnapshot(), "items[0].role.roleExternalId"),
                parseText(log.getDiffSnapshot(), "items[0].role.roleName")
            ),
            null,
            null,
            log.getChangeReason(),
            log.getCreatedAt()
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
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on USER:" + userId);
        }

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

        List<ResourceEntity> entities = resourceEntityMapper.selectValidByIds(tenantId, permittedIds).stream().toList();

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
