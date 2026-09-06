package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewFilter;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserEffectiveRolesReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.*;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourcePermissionViewResp.RoleGrantInfo;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionViewResp.PermissionItem;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserPermissionViewResp.ResourcePermissionView;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserPermissionViewResp.SourceRoleView;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.entity.*;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.*;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
import cn.ac.fage.accessmesh.access.permission.util.PermViewAssembler;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.ScopeModeSupport;
import cn.ac.fage.accessmesh.access.permission.vo.ConditionEvaluationDetail;
import cn.ac.fage.accessmesh.access.permission.vo.MutexFilterResult;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
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
 * explain（T-PERM-033）：门禁为被查目标实例 VIEW；条件评估明细/互斥丢弃明细/
 * 按权限键过滤的 recentChanges 内嵌返回；独立 recent-changes 端点在 LogQueryAppService。
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class PermissionViewAppServiceImpl implements PermissionViewAppService {

    /** explain 评估上下文来源：管理员输入 */
    private static final String CONTEXT_SOURCE_ADMIN_INPUT = "ADMIN_INPUT";

    /** explain 评估上下文来源：回退当前请求环境 */
    private static final String CONTEXT_SOURCE_CURRENT_REQUEST = "CURRENT_REQUEST";

    /** explain recentChanges 候选池上限（按目标 + 窗口取最近 N 条再做权限键过滤） */
    private static final int RECENT_CANDIDATE_LIMIT = 200;

    /** explain recentChanges 过滤后返回上限 */
    private static final int RECENT_RESULT_LIMIT = 50;

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
    private final PermissionConditionDomainService conditionDomainService;
    private final PermissionConflictDomainService conflictDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param abstractRoleMapper        抽象角色数据访问层
     * @param resourceEntityMapper      资源实体数据访问层
     * @param operationPermissionMapper 操作权限数据访问层
     * @param rolePermMapper            角色资源权限数据访问层
     * @param subjectDomainService      主体领域服务
     * @param typeResolutionService     类型解析服务
     * @param auditDomainService        审计领域服务
     * @param objectMapper              JSON解析器
     * @param engine                    权限查询引擎
     * @param permViewAssembler         权限视图装配器
     * @param conditionDomainService    权限条件领域服务（explain 条件评估明细）
     * @param conflictDomainService     权限冲突领域服务（explain 互斥丢弃明细）
     */
    public PermissionViewAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                         ResourceEntityMapper resourceEntityMapper,
                                         OperationPermissionMapper operationPermissionMapper,
                                         RoleResourcePermissionMapper rolePermMapper,
                                         SubjectDomainService subjectDomainService,
                                         TypeResolutionService typeResolutionService,
                                         AuditDomainService auditDomainService,
                                         ObjectMapper objectMapper,
                                         PermQueryEngine engine,
                                         PermViewAssembler permViewAssembler,
                                         PermissionConditionDomainService conditionDomainService,
                                         PermissionConflictDomainService conflictDomainService) {
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
        this.conditionDomainService = conditionDomainService;
        this.conflictDomainService = conflictDomainService;
    }

    /**
     * 查询用户或角色的有效权限视图
     * <p>
     * 查询用户通过所有角色获得的聚合权限，或角色的直接权限。
     * 支持按资源类型、操作码、来源角色等维度过滤。
     * 使用 forUserView 查询管线 + PermViewAssembler 过滤分页。
     * 对USER目标需要USER_VIEW权限，对ROLE目标需要ROLE_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限视图查询请求
     * @return 有效权限响应，包含权限项列表和分页信息
     * @throws SecurityException 无权限时抛出
     */
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
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on USER:" + userId);
            }
            PageResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, req);
            List<PermissionEffectivePermissionsResp.EffectivePermissionItem> items = paged.items().stream().map(v ->
                new PermissionEffectivePermissionsResp.EffectivePermissionItem(
                    v.resourceTypeCode(), v.resourceCode(), v.resourceName(), v.codeType(),
                    v.operationCodes(), v.scopeMode(),
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
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on ROLE:" + roleId);
            }
        } else if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE");
        }
        PageResp<PermissionItem> paged = getRolePermissionItemsPaged(
            tenantId, roleId, pageNum, pageSize);
        List<PermissionEffectivePermissionsResp.EffectivePermissionItem> items = paged.items().stream().map(p ->
            new PermissionEffectivePermissionsResp.EffectivePermissionItem(
                p.resourceTypeCode(), p.resourceCode(), p.resourceName(), null,
                p.operationCode() == null ? List.of() : List.of(p.operationCode()),
                p.scopeMode(), List.of(), 0, false,
                p.id() == null ? List.of() : List.of(p.id())
            )
        ).toList();
        return new PermissionEffectivePermissionsResp(PermConstants.TargetType.ROLE, items, (int) paged.total(), pageNum, pageSize, paged.hasNext());
    }

    /**
     * 获取用户权限视图（带过滤和分页）
     * <p>
     * 解析用户角色，应用过滤条件，调用权限引擎查询，
     * 通过PermViewAssembler进行结果过滤和分页组装。
     * 支持按来源角色、角色类型、资源类型、操作码等维度过滤。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param req      权限视图查询请求
     * @return 分页后的资源权限视图列表
     */
    PageResp<ResourcePermissionView> getUserPermissionsWithFilters(Long tenantId, Long userId, UserPermissionViewReq req) {
        // 1. 解析用户角色并过滤
        Set<Long> allRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        if (allRoleIds.isEmpty()) {
            int pageNum = PageUtil.pageNum(req.pageNum());
            int pageSize = PageUtil.pageSize(req.pageSize());
            return new PageResp<>(List.of(), 0L, pageNum, pageSize, false);
        }

        Set<Long> filteredRoleIds = filterRoleIds(tenantId, allRoleIds,
            req.sourceRoleExternalId(), req.roleTypeCode(), req.domainCode());
        if (filteredRoleIds.isEmpty()) {
            int pageNum = PageUtil.pageNum(req.pageNum());
            int pageSize = PageUtil.pageSize(req.pageSize());
            return new PageResp<>(List.of(), 0L, pageNum, pageSize, false);
        }

        // 2. 构建 forUserView 查询
        PermQuery query = PermQuery.forUserView(tenantId, userId);
        query.setRoleIds(filteredRoleIds);

        // 3. 调引擎获取全量结果
        PermResult result = engine.query(query);
        if (!result.allowed()) {
            int pageNum = PageUtil.pageNum(req.pageNum());
            int pageSize = PageUtil.pageSize(req.pageSize());
            return new PageResp<>(List.of(), 0L, pageNum, pageSize, false);
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

    /**
     * 从视图结果构建分页响应
     * <p>
     * 将PermViewResult中的权限条目按scopeAll和实例级分别组装，
     * 聚合后进行内存分页。
     * </p>
     *
     * @param viewResult 视图结果
     * @param filter     过滤条件
     * @param tenantId   租户ID
     * @return 分页后的资源权限视图列表
     */
    private PageResp<ResourcePermissionView> buildResponseFromView(PermViewResult viewResult, PermViewFilter filter, Long tenantId) {
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

        return new PageResp<>(pagedItems, totalItems,
            pageNum, pageSize, hasNext);
    }

    /**
     * 构建scopeAll类型的权限视图项
     * <p>
     * 对于scopeAll权限（无需指定具体资源实例），按资源类型聚合生成视图项。
     * </p>
     *
     * @param resourceTypeCode 资源类型编码
     * @param entries          权限条目列表
     * @param viewResult       视图结果
     * @param filter           过滤条件
     * @return scopeAll权限视图项
     */
    private ResourcePermissionView buildScopeAllPermissionView(
            String resourceTypeCode, List<RolePermEntry> entries, PermViewResult viewResult,
            PermViewFilter filter) {
        Set<String> operationCodes = operationCodesForEntries(entries, viewResult);
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
            ScopeMode.ALL,
            new ArrayList<>(operationCodes),
            sourceRoles,
            sourceRoleCount,
            sourceRolesTruncated,
            new ArrayList<>(matchedPermissionIds)
        );
    }

    /**
     * 构建实例级权限视图项
     * <p>
     * 对于实例级权限（绑定具体资源实体），聚合该资源的所有权限条目生成视图项。
     * </p>
     *
     * @param resourceId   资源实体ID
     * @param entries      权限条目列表
     * @param resource     资源实体
     * @param viewResult   视图结果
     * @param filter       过滤条件
     * @return 实例级权限视图项
     */
    private ResourcePermissionView buildResourcePermissionView(
            Long resourceId,
            List<RolePermEntry> entries,
            ResourceEntity resource,
            PermViewResult viewResult,
            PermViewFilter filter) {

        // 操作码
        Set<String> operationCodes = operationCodesForEntries(entries, viewResult);

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

        return new ResourcePermissionView(
            resourceId,
            viewResult.getDomainCodeMap().get(resourceId),
            resource.getCode(),
            resource.getName(),
            viewResult.getResourceMap().get(resourceId) != null && viewResult.getResourceTypeCodeMap() != null
                ? viewResult.getResourceTypeCodeMap().get(resourceId) : null,
            resource.getCodeType(),
            ScopeMode.INSTANCE,
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

    /**
     * 查询资源的权限视图
     * <p>
     * 查询指定资源实体上所有角色的权限授予情况。
     * 返回每个角色对该资源的操作码列表和授予来源。
     * 需要RESOURCE_VIEW权限。
     * </p>
     *
     * @param tenantId         租户ID
     * @param domainCode       业务域编码
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型
     * @return 资源权限视图响应，包含角色授予信息列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType) {
        Long operatorId = OperatorContext.getOperatorId();
        Long resourceEntityId = typeResolutionService.resolveResourceId(tenantId, resourceTypeCode, resourceCode, codeType, domainCode);
        if (resourceEntityId != null) {
            if (!engine.hasPermissionByEntityId(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceEntityId, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on RESOURCE:" + resourceEntityId);
            }
        } else {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW)) {
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

    /**
     * 查询角色的权限视图
     * <p>
     * 查询指定角色的所有权限授予情况。
     * 返回权限项列表，包含资源信息、操作码、依赖关系、条件等。
     * 需要ROLE_VIEW权限。
     * </p>
     *
     * @param tenantId       租户ID
     * @param domainCode     业务域编码
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部ID
     * @param expandSub      是否展开子角色权限（当前未实现）
     * @return 角色权限视图响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public RolePermissionViewResp getRolePermissions(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, boolean expandSub) {
        Long operatorId = OperatorContext.getOperatorId();
        Long roleId = typeResolutionService.resolveRoleId(tenantId, roleTypeCode, roleExternalId, domainCode);
        if (roleId != null) {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on ROLE:" + roleId);
            }
        } else {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
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
                    ScopeModeSupport.fromScopeAll(p.getScopeAll()),
                    p.getDependOn(),
                    p.getConditionId(),
                    p.getCanGrant(),
                    p.getGrantSource()
                );
            })
            .collect(Collectors.toList());

        return new RolePermissionViewResp(roleId, role.getName(), typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()), items);
    }

    /**
     * 分页查询角色权限项
     * <p>
     * 查询角色的权限条目并进行内存分页。
     * 用于ROLE类型的有效权限查询。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页后的权限项列表
     */
    private PageResp<PermissionItem> getRolePermissionItemsPaged(Long tenantId, Long roleId, int pageNum, int pageSize) {
        if (roleId == null) {
            return new PageResp<>(List.of(), 0L, pageNum, pageSize, false);
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
                ScopeModeSupport.fromScopeAll(p.getScopeAll()),
                p.getDependOn(),
                p.getConditionId(),
                p.getCanGrant(),
                p.getGrantSource()
            );
        }).toList();
        return new PageResp<>(items, total, pageNum, pageSize, offset + items.size() < total);
    }

    /**
     * 解释权限判定结果
     * <p>
     * 对指定用户或角色进行权限判定，并返回详细的解释信息：
     * 判定结果、命中的角色、匹配的权限ID、候选命中条目的条件评估明细（脱敏）、
     * 被互斥规则丢弃的条目、可选的按权限键过滤的最近变更记录。
     * 门禁（T-PERM-033 设计定案）：与 {@link #getEffectivePermissions} 同款目标实例
     * VIEW 检查——查谁就要对谁有 VIEW（ROLE 未解析时类型级兜底；USER 未解析不检查，
     * 返回 USER_NOT_FOUND）。
     * 条件评估上下文：管理员输入（req.context.clientIp）优先，缺省回退当前请求环境；
     * 日期/时间类条件按服务进程系统时钟评估（与运行时判定一致，不可模拟）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限解释请求
     * @return 权限解释响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public PermissionExplainResp explain(Long tenantId, PermissionExplainReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        boolean roleTarget = PermConstants.TargetType.ROLE.equalsIgnoreCase(req.targetType());

        // 预解析目标（门禁 + 查询共用，避免重复解析）
        Long targetRoleId = roleTarget
            ? typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode())
            : null;
        Long userId = roleTarget
            ? null
            : typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());

        // 门禁：目标实例 VIEW（T-PERM-033 设计定案，与 effective-permissions 同款）
        if (roleTarget) {
            if (targetRoleId != null) {
                if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(targetRoleId), OperationCodeConstants.VIEW)) {
                    throw new SecurityException("Permission denied: VIEW on ROLE:" + targetRoleId);
                }
            } else if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on ROLE");
            }
        } else if (userId != null
            && !engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on USER:" + userId);
        }

        // 条件评估上下文：管理员输入优先，缺省回退当前请求（T-PERM-033）
        String evaluatedClientIp;
        String contextSource;
        if (req.context() != null && req.context().clientIp() != null && !req.context().clientIp().isBlank()) {
            evaluatedClientIp = req.context().clientIp().trim();
            contextSource = CONTEXT_SOURCE_ADMIN_INPUT;
        } else {
            evaluatedClientIp = HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest());
            contextSource = CONTEXT_SOURCE_CURRENT_REQUEST;
        }
        Map<String, Object> evalContext = evaluatedClientIp == null
            ? Map.<String, Object>of() : Map.of("clientIp", evaluatedClientIp);

        boolean scopeAll = ScopeModeSupport.toScopeAllForGrant(req.scopeMode(), req.resourceCode(), req.codeType());
        String queryResourceCode = scopeAll ? null : req.resourceCode();
        String queryCodeType = scopeAll ? null : req.codeType();

        // 判定查询（allowed/reason/matched id 集合由引擎完整评估得出）
        // T-API-002：check 线格式响应已裁 matched id 字段族，explain 改用内部载体直接消费引擎结果
        ExplainCheckOutcome check;
        if (roleTarget && targetRoleId == null) {
            check = ExplainCheckOutcome.deny("ROLE_NOT_FOUND");
        } else if (!roleTarget && userId == null) {
            check = ExplainCheckOutcome.deny("USER_NOT_FOUND");
        } else {
            PermQuery q = buildExplainPermQuery(tenantId, req, roleTarget, targetRoleId, userId,
                scopeAll, queryResourceCode, queryCodeType, evalContext);
            check = ExplainCheckOutcome.of(engine.query(q));
        }

        List<PermissionExplainResp.SourceRole> sourceRoles = List.of();
        if (Boolean.TRUE.equals(req.includeSourceRoles())) {
            if (roleTarget) {
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
            } else if (!check.matchedRoleIds().isEmpty()) {
                sourceRoles = abstractRoleMapper.selectValidByIds(tenantId, new java.util.HashSet<>(check.matchedRoleIds())).stream()
                    .map(role -> new PermissionExplainResp.SourceRole(
                        typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()),
                        role.getExternalId(),
                        role.getName(),
                        List.of()
                    )).toList();
            }
        }

        // 解释明细：候选命中（条件/互斥过滤前）→ 条件逐项评估 → 互斥丢弃
        List<ConditionEvaluationDetail> conditionDetails = List.of();
        List<PermissionExplainResp.ConflictDrop> conflictDrops = List.of();
        boolean targetResolved = roleTarget ? targetRoleId != null : userId != null;
        if (targetResolved) {
            PermQuery rawQuery = buildExplainPermQuery(tenantId, req, roleTarget, targetRoleId, userId,
                scopeAll, queryResourceCode, queryCodeType, evalContext);
            rawQuery.setEvaluateConditions(false);
            rawQuery.setEvaluateConflicts(false);
            // 候选查询只消费 allEntries，关闭辅助实体批量加载（资源/操作/角色映射）
            rawQuery.setIncludeResources(false);
            rawQuery.setIncludeOperations(false);
            rawQuery.setIncludeRoles(false);
            List<RolePermEntry> rawCandidates = engine.query(rawQuery).allEntries();

            conditionDetails = conditionDomainService.evaluateDetailed(tenantId, rawCandidates, evalContext);
            List<RolePermEntry> conditionPassed = applyConditionResults(rawCandidates, conditionDetails);
            MutexFilterResult mutex = conflictDomainService.filterPermMutexWithDrops(tenantId, conditionPassed);
            conflictDrops = mutex.drops().stream()
                .map(drop -> new PermissionExplainResp.ConflictDrop(
                    drop.entry().permissionId(), drop.entry().roleId(),
                    drop.ruleId(), drop.firstOperationCode(), drop.secondOperationCode()))
                .toList();
        }

        // 最近变更：按权限键过滤（USER 目标保留角色分配/回收事件，T-PERM-033 设计定案）
        List<RecentChangeResp> recentChanges = List.of();
        if (Boolean.TRUE.equals(req.includeRecentChanges()) && targetResolved) {
            int recentDays = req.recentDays() == null ? 30 : Math.max(req.recentDays(), 1);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime since = now.minusDays(recentDays);
            List<PermissionChangeLog> candidates = auditDomainService.queryRecentChanges(
                tenantId, roleTarget ? null : userId, roleTarget ? targetRoleId : null,
                since, now, null, 0, RECENT_CANDIDATE_LIMIT);
            recentChanges = candidates.stream()
                .map(log -> toRelatedRecentChange(log, req, roleTarget))
                .filter(Objects::nonNull)
                .limit(RECENT_RESULT_LIMIT)
                .toList();
        }

        return new PermissionExplainResp(
            req.targetType(),
            check.allowed(),
            check.reason(),
            new PermissionExplainResp.PermissionKey(
                req.domainCode(), req.resourceTypeCode(), queryResourceCode, queryCodeType, req.operationCode(), req.scopeMode()
            ),
            sourceRoles,
            check.matchedPermissionIds(),
            recentChanges,
            contextSource,
            evaluatedClientIp,
            conditionDetails.stream()
                .map(d -> new PermissionExplainResp.ConditionEvaluation(
                    d.conditionId(), d.permissionId(), d.roleId(), d.status(), d.logic(), d.passed(),
                    d.items().stream()
                        .map(i -> new PermissionExplainResp.ConditionItem(i.type(), i.maskedParams(), i.matched()))
                        .toList()))
                .toList(),
            conflictDrops
        );
    }

    /**
     * explain 判定内部载体（T-API-002）：check 线格式响应已裁 matched id 字段族，
     * explain 内部仍需 matched 角色/权限 id 集合（来源角色反查、matchedPermissionIds 回填），
     * 改为直接消费引擎 {@link PermResult}，不经线格式 DTO 中转。
     */
    private record ExplainCheckOutcome(boolean allowed, String reason,
                                       Set<Long> matchedRoleIds,
                                       List<Long> matchedPermissionIds) {

        static ExplainCheckOutcome deny(String reason) {
            return new ExplainCheckOutcome(false, reason, Set.of(), List.of());
        }

        static ExplainCheckOutcome of(PermResult r) {
            return new ExplainCheckOutcome(
                r.allowed(),
                r.allowed() ? null : (r.reason() != null ? r.reason() : "DENIED"),
                r.matchedRoleIds(),
                List.copyOf(r.matchedPermissionIds()));
        }
    }

    /**
     * 构建 explain 判定查询（USER 分支 forAuthCheck / ROLE 分支 forScopeQuery，
     * 形状与判定语义和历史实现一致；上下文为条件评估所用）
     */
    private PermQuery buildExplainPermQuery(Long tenantId, PermissionExplainReq req, boolean roleTarget,
                                            Long targetRoleId, Long userId, boolean scopeAll,
                                            String queryResourceCode, String queryCodeType,
                                            Map<String, Object> evalContext) {
        PermQuery q;
        if (roleTarget) {
            q = PermQuery.forScopeQuery(tenantId, null,
                Set.of(req.resourceTypeCode()), Set.of(req.operationCode()));
            q.setRoleIds(Set.of(targetRoleId));
            q.setResourceCodes(scopeAll ? null : Set.of(queryResourceCode));
            q.setCodeType(queryCodeType);
            q.setDomainCode(req.domainCode());
            q.setQueryScopeAll(scopeAll);
            q.setQueryInstance(!scopeAll);
            q.setEvaluateConditions(true);
            q.setEvaluateConflicts(true);
            q.setEvaluateMatchesBit(true);
        } else {
            q = PermQuery.forAuthCheck(tenantId, userId,
                req.resourceTypeCode(), queryResourceCode, req.operationCode());
            q.setCodeType(queryCodeType);
        }
        q.setContext(evalContext);
        return q;
    }

    /**
     * 按评估明细还原条件通过的条目（无条件条目直接通过；挂条件条目按明细 passed 判定）
     */
    private List<RolePermEntry> applyConditionResults(List<RolePermEntry> rawCandidates,
                                                      List<ConditionEvaluationDetail> details) {
        List<RolePermEntry> passed = new ArrayList<>();
        int detailIndex = 0;
        for (RolePermEntry entry : rawCandidates) {
            if (entry.conditionId() == null || !entry.hasCondition()) {
                passed.add(entry);
                continue;
            }
            ConditionEvaluationDetail detail = detailIndex < details.size() ? details.get(detailIndex++) : null;
            if (detail == null || detail.passed()) {
                passed.add(entry);
            }
        }
        return passed;
    }

    /**
     * 变更日志 → 与目标权限键相关的最近变更（不相关返回 null）。
     * <p>
     * 匹配规则（T-PERM-033 设计定案）：
     * 含 permission 键的事件按 6 字段匹配（resourceTypeCode/operationCode/scopeMode 精确，
     * domainCode/resourceCode/codeType 请求侧为 null 时通配），并取首个匹配 item 的摘要；
     * USER 目标额外保留 USER_ROLE_CHANGE（角色分配/回收直接回答"为什么有/没有"）。
     * </p>
     */
    private RecentChangeResp toRelatedRecentChange(PermissionChangeLog log, PermissionExplainReq req, boolean roleTarget) {
        JsonNode root = parseTree(log.getDiffSnapshot());
        if (root == null) {
            return null;
        }
        JsonNode items = root.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return null;
        }
        String eventType = nodeText(root, "eventType");
        if (!roleTarget && "USER_ROLE_CHANGE".equals(eventType)) {
            return buildRecentChange(log, root, items.get(0), "DIRECT");
        }
        for (JsonNode item : items) {
            JsonNode permission = item.get("permission");
            if (permission != null && matchesPermissionKey(permission, req)) {
                return buildRecentChange(log, root, item, roleTarget ? "DIRECT" : "POSSIBLE");
            }
        }
        return null;
    }

    /**
     * 快照 permission 节点与 explain 请求权限键的 6 字段匹配（null 请求字段通配）：
     * resourceTypeCode/operationCode 精确；domainCode/resourceCode/codeType 请求侧
     * 为 null 时通配、非 null 时精确；scopeMode 请求侧必传（入口已校验）。
     */
    private boolean matchesPermissionKey(JsonNode permission, PermissionExplainReq req) {
        if (!Objects.equals(nodeText(permission, "resourceTypeCode"), req.resourceTypeCode())
            || !Objects.equals(nodeText(permission, "operationCode"), req.operationCode())) {
            return false;
        }
        if (req.domainCode() != null
            && !Objects.equals(nodeText(permission, "domainCode"), req.domainCode())) {
            return false;
        }
        ScopeMode snapshotMode = ScopeModeSupport.fromSnapshot(
            nodeText(permission, "scopeMode"), nodeBoolean(permission, "scopeAll"));
        if (snapshotMode != req.scopeMode()) {
            return false;
        }
        if (ScopeMode.ALL.equals(req.scopeMode())) {
            // ALL 键：快照不应携带具体实例编码
            return nodeText(permission, "resourceCode") == null;
        }
        // INSTANCE 键：resourceCode/codeType 请求侧为 null 时通配，非 null 时精确匹配
        String snapshotResourceCode = nodeText(permission, "resourceCode");
        if (req.resourceCode() != null && !Objects.equals(snapshotResourceCode, req.resourceCode())) {
            return false;
        }
        return req.codeType() == null || Objects.equals(nodeText(permission, "codeType"), req.codeType());
    }

    /**
     * 从匹配 item 构建最近变更响应（impactLevel：DIRECT=直接命中查询对象 / POSSIBLE=间接影响）
     */
    private RecentChangeResp buildRecentChange(PermissionChangeLog log, JsonNode root, JsonNode item, String impactLevel) {
        JsonNode permission = item.get("permission");
        JsonNode role = item.get("role");
        return new RecentChangeResp(
            log.getId(),
            nodeText(root, "eventType"),
            nodeText(item, "changeType"),
            impactLevel,
            nodeText(item, "message"),
            new RecentChangeResp.PermissionKey(
                nodeText(permission, "domainCode"),
                nodeText(permission, "resourceTypeCode"),
                nodeText(permission, "resourceCode"),
                nodeText(permission, "codeType"),
                nodeText(permission, "operationCode"),
                ScopeModeSupport.fromSnapshot(
                    nodeText(permission, "scopeMode"), nodeBoolean(permission, "scopeAll"))
            ),
            new RecentChangeResp.SourceRole(
                nodeText(role, "roleTypeCode"),
                nodeText(role, "roleExternalId"),
                nodeText(role, "roleName")
            ),
            null,
            null,
            log.getChangeReason(),
            log.getCreatedAt()
        );
    }

    /**
     * 解析 JSON 字符串为树（空白/解析失败返回 null）
     */
    private JsonNode parseTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 节点字段文本值（缺失/null 返回 null）
     */
    private String nodeText(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 节点字段布尔值（缺失/null 返回 null）
     */
    private Boolean nodeBoolean(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    /**
     * 查询用户的有效角色列表
     * <p>
     * 查询用户通过直接分配、继承、组 membership 等方式获得的所有有效角色。
     * 需要USER_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      有效角色查询请求
     * @return 有效角色响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId != null) {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.VIEW)) {
                throw new SecurityException("Permission denied: VIEW on USER:" + userId);
            }
        } else {
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.VIEW)) {
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

    /**
     * 查询用户的资源权限树
     * <p>
     * 查询用户有权限的资源实体，并按父子关系构建树形结构。
     * 用于展示资源的层级权限视图。
     * 需要USER_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param req      资源树查询请求
     * @return 资源权限树响应列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ResourcePermissionTreeResp> getUserResourceTree(Long tenantId, Long userId, UserResourceTreeReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on USER:" + userId);
        }

        UserPermissionViewReq treeReq = new UserPermissionViewReq(
            PermConstants.TargetType.USER, req.subjectTypeCode(), req.subjectExternalId(), req.domainCode(),
            null, null, req.resourceTypeCodes(), req.operationCodes(),
            req.resourceKeyword(), null, false, false, false, null, 1, 10000
        );
        PageResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, treeReq);
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

    /**
     * 构建资源权限树节点
     * <p>
     * 递归构建资源树节点，处理循环引用避免无限递归。
     * </p>
     *
     * @param entityId   资源实体ID
     * @param viewMap    权限视图映射
     * @param entityMap  资源实体映射
     * @param childrenMap 子节点ID列表映射
     * @param tenantId   租户ID
     * @param visited    已访问节点集合（防止循环）
     * @return 资源权限树节点
     */
    private ResourcePermissionTreeResp buildPermissionTreeNode(
            Long entityId,
            Map<Long, ResourcePermissionView> viewMap,
            Map<Long, ResourceEntity> entityMap,
            Map<Long, List<Long>> childrenMap,
            Long tenantId,
            Set<Long> visited) {
        if (visited.contains(entityId)) {
            return new ResourcePermissionTreeResp(
                entityId, null, null, null, null, PermConstants.CodeType.DEFAULT, ScopeMode.INSTANCE, List.of(), List.of()
            );
        }
        visited.add(entityId);

        ResourcePermissionView view = viewMap.get(entityId);
        ResourceEntity entity = entityMap.get(entityId);
        String domainCode = null;
        String resourceTypeCode = null;
        String codeType = PermConstants.CodeType.DEFAULT;
        List<String> operationCodes = List.of();
        ScopeMode scopeMode = ScopeMode.INSTANCE;
        String resourceCode = null;
        String resourceName = null;

        if (view != null) {
            domainCode = view.domainCode();
            resourceTypeCode = view.resourceTypeCode();
            codeType = view.codeType();
            operationCodes = view.operationCodes();
            scopeMode = view.scopeMode();
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
            resourceTypeCode, codeType, scopeMode, operationCodes, children
        );
    }

    /**
     * 用户有效权限码聚合查询（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 实现路径（G-2 独立方法，不污染 {@link #getEffectivePermissions} 分页路径）：
     * <ol>
     *   <li>解析 userId 并门禁 USER:VIEW</li>
     *   <li>解析有效角色集合</li>
     *   <li>调 {@link PermQueryEngine#query(PermQuery)} 走 forUserView 管线</li>
     *   <li>用 {@link PermViewAssembler#assemble} 应用资源类型白名单 + 排除 API + 不分页</li>
     *   <li>遍历引擎返回的 effective 操作投影，拼成
     *       {@code "<resourceTypeCode>:<operationCode>"}，写入 LinkedHashSet 去重</li>
     * </ol>
     * <p>
     * 不分页 / 不截断：所有 entries 全量遍历，确保任何用户的所有有效权限码均被返回。
     */
    @Override
    public UserEffectivePermissionCodesResp getEffectivePermissionCodesForManage(Long tenantId, UserEffectivePermissionCodesReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        // 解析目标用户
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new UserEffectivePermissionCodesResp(List.of());
        }

        // 门禁：自查豁免；查他人时操作者需对被查用户有 USER:VIEW，防任意登录用户枚举 ID 越权读取他人权限码。
        if (!Objects.equals(operatorId, userId)
            && !engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on USER:" + userId);
        }
        return getEffectivePermissionCodes(tenantId, req);
    }

    @Override
    public UserEffectivePermissionCodesResp getEffectivePermissionCodes(Long tenantId, UserEffectivePermissionCodesReq req) {
        PermViewResult viewResult = buildEffectiveView(tenantId, req);
        if (viewResult == null) {
            return new UserEffectivePermissionCodesResp(List.of());
        }

        // 从 effective 操作投影全量提取 resourceTypeCode:operationCode
        //    PermViewResult.resourceTypeCodeMap 是 resourceId -> typeCode（实例级 view 用），
        //    本接口需要 resourceType(int) -> typeCode，需要直接调 typeResolutionService 重新解析。
        Set<Integer> resourceTypeValues = viewResult.getEntries().stream()
            .map(RolePermEntry::resourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> typeCodeMap = resourceTypeValues.isEmpty()
            ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        Set<String> permCodes = new LinkedHashSet<>();
        for (PermResult.EffectiveOperationEntry entry : viewResult.getEffectiveOperationEntries()) {
            if (entry.resourceType() == null
                || entry.operationCode() == null) {
                continue;
            }
            String resourceTypeCode = typeCodeMap.get(entry.resourceType());
            if (resourceTypeCode == null) {
                continue;
            }
            permCodes.add(resourceTypeCode + ":" + entry.operationCode());
        }
        return new UserEffectivePermissionCodesResp(new ArrayList<>(permCodes));
    }

    @Override
    public EffectiveResourceAccess getEffectiveResourceAccess(Long tenantId, UserEffectivePermissionCodesReq req) {
        PermViewResult viewResult = buildEffectiveView(tenantId, req);
        if (viewResult == null) {
            return new EffectiveResourceAccess(Set.of(), Set.of());
        }
        // scopeAll 条目：resourceEntityId 为 null（全范围授权），按资源类型收集
        Set<Integer> allScopeTypes = new LinkedHashSet<>();
        Set<Long> resourceEntityIds = new LinkedHashSet<>();
        for (PermResult.EffectiveOperationEntry entry : viewResult.getEffectiveOperationEntries()) {
            if (entry.resourceType() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.scopeAll())) {
                allScopeTypes.add(entry.resourceType());
            } else if (entry.resourceEntityId() != null) {
                resourceEntityIds.add(entry.resourceEntityId());
            }
        }
        return new EffectiveResourceAccess(allScopeTypes, resourceEntityIds);
    }

    /**
     * 构建用户有效权限视图（getEffectivePermissionCodes 与 getEffectiveResourceAccess 的公共管线）。
     * <p>
     * 步骤：解析 userId → 解析有效角色 → forUserView 引擎管线 →
     * 装配器按资源类型白名单过滤（排除 API 资源、包含 scope 权限、不分页）。
     * 任一前置步骤失败返回 null（调用方按「无权限」处理）。
     * </p>
     * <p>
     * 本管线不设 {@code USER:VIEW} 门禁：调用方各自负责入口门禁——
     * permission 域独立 HTTP 入口走 {@link #getEffectivePermissionCodesForManage}
     * （自查豁免 + 查他人需 USER:VIEW）；query 包内部调用由其入口 Controller 门禁
     * （自查豁免 + {@code USER:VIEW}）兜底。
     * </p>
     */
    private PermViewResult buildEffectiveView(Long tenantId, UserEffectivePermissionCodesReq req) {
        // 1. 解析 userId
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return null;
        }

        // 2. 解析有效角色
        Set<Long> roleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        if (roleIds.isEmpty()) {
            return null;
        }

        // 3. 调引擎获取全量结果
        PermQuery query = PermQuery.forUserView(tenantId, userId);
        query.setRoleIds(roleIds);
        PermResult result = engine.query(query);
        if (!result.allowed()) {
            return null;
        }

        // 4. 通过装配器过滤（仅按资源类型白名单 + 排除 API），关键差异：
        //    - 不传 pageNum/pageSize（PermViewAssembler.paginate 注释明说「分页延迟到调用方聚合后执行」，
        //      assemble 总是返回全量已过滤 entries，故此处天然不分页）
        //    - 不需要 sourceRoles（权限码下发无需来源角色）
        PermViewFilter filter = new PermViewFilter();
        filter.setResourceTypes(req.resourceTypeCodes() == null ? null : new LinkedHashSet<>(req.resourceTypeCodes()));
        filter.setExcludeApiResources(true);
        filter.setIncludeScopePermissions(true);
        filter.setIncludeSourceRoles(false);
        filter.setSourceRoleLimit(0);
        // pageNum/pageSize 不影响 entries 内容（只影响 buildResponseFromView 的截断），
        // 此处显式设为 1 避免 PermViewAssembler.paginate 走 "<= 0 用默认 20" 分支
        filter.setPageNum(1);
        filter.setPageSize(Integer.MAX_VALUE);

        return permViewAssembler.assemble(tenantId, result, filter);
    }

    private Set<String> operationCodesForEntries(List<RolePermEntry> entries, PermViewResult viewResult) {
        Set<String> allowedEntryKeys = entries.stream()
            .map(this::effectiveSourceKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> operationCodes = viewResult.getEffectiveOperationEntries().stream()
            .filter(e -> allowedEntryKeys.contains(effectiveSourceKey(e)))
            .map(PermResult.EffectiveOperationEntry::operationCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!operationCodes.isEmpty()) {
            return operationCodes;
        }

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
        return operationCodes;
    }

    private String effectiveSourceKey(RolePermEntry entry) {
        return entry.permissionId() + "|"
            + entry.roleId() + "|"
            + entry.resourceEntityId() + "|"
            + entry.resourceType() + "|"
            + entry.grantedBits() + "|"
            + entry.scopeAll();
    }

    private String effectiveSourceKey(PermResult.EffectiveOperationEntry entry) {
        return entry.permissionId() + "|"
            + entry.roleId() + "|"
            + entry.resourceEntityId() + "|"
            + entry.resourceType() + "|"
            + entry.grantedBits() + "|"
            + entry.scopeAll();
    }

}
