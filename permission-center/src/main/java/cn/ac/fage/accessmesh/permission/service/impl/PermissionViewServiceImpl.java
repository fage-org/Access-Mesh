package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
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
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.LogQueryService;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import cn.ac.fage.accessmesh.permission.service.context.PermissionQueryContext;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限视图服务实现类
 * <p>
 * 提供用户权限视图、角色权限视图、资源权限视图、权限解释、变更历史查询等功能。
 * 所有查询均通过PermQueryEngine进行权限校验，确保操作安全。
 * 采用批量加载策略避免N+1查询问题，使用PermissionQueryContext封装查询上下文。
 * 权限视图查询采用六阶段处理流程：准备上下文、加载角色、加载权限、过滤权限、分页结果、组装响应。
 * </p>
 *
 * TODO: 构造函数依赖过多(12个)，违反单一职责原则
 * 建议：拆分权限视图查询/变更日志查询职责
 * 优先级：P2（非阻塞，建议在下次大版本重构时处理）
 */
@Service
public class PermissionViewServiceImpl implements PermissionViewService {

    private final AbstractUserMapper abstractUserMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final BizDomainMapper bizDomainMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final UserRoleDomainService userRoleDomainService;
    private final DomainClassifyService domainClassifyService;
    private final EntityBatchLoadDomainService entityBatchLoadDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermissionService permissionService;
    private final LogQueryService logQueryService;
    private final ObjectMapper objectMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param abstractUserMapper          抽象用户数据访问层
     * @param abstractRoleMapper          抽象角色数据访问层
     * @param resourceEntityMapper        资源实体数据访问层
     * @param operationPermissionMapper   操作权限数据访问层
     * @param bizDomainMapper             业务域数据访问层
     * @param rolePermMapper              角色资源权限数据访问层
     * @param userRoleDomainService       用户角色领域服务
     * @param typeResolutionService       类型解析服务
     * @param permissionService           权限服务
     * @param logQueryService             日志查询服务
     * @param objectMapper                JSON对象映射器
     * @param engine                      权限查询引擎
     */
    public PermissionViewServiceImpl(AbstractUserMapper abstractUserMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     ResourceEntityMapper resourceEntityMapper,
                                     OperationPermissionMapper operationPermissionMapper,
                                     BizDomainMapper bizDomainMapper,
                                     RoleResourcePermissionMapper rolePermMapper,
                                     UserRoleDomainService userRoleDomainService,
                                     DomainClassifyService domainClassifyService,
                                     EntityBatchLoadDomainService entityBatchLoadDomainService,
                                     TypeResolutionService typeResolutionService,
                                     PermissionService permissionService,
                                     LogQueryService logQueryService,
                                     ObjectMapper objectMapper,
                                     PermQueryEngine engine) {
        this.abstractUserMapper = abstractUserMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleDomainService = userRoleDomainService;
        this.domainClassifyService = domainClassifyService;
        this.entityBatchLoadDomainService = entityBatchLoadDomainService;
        this.typeResolutionService = typeResolutionService;
        this.permissionService = permissionService;
        this.logQueryService = logQueryService;
        this.objectMapper = objectMapper;
        this.engine = engine;
    }

    /**
     * 获取用户权限视图
     * <p>
     * 查询指定用户的所有权限信息，返回用户类型、外部ID、名称及权限列表。
     * 需要USER_VIEW权限。权限列表默认不包含API资源和作用域权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户权限视图响应，用户不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public UserPermissionViewResp getUserPermissions(Long tenantId, Long userId) {
        // 权限校验：查看用户需要USER_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW);

        PaginatedResp<ResourcePermissionView> paged = getUserPermissionsWithFilters(tenantId, userId, new UserPermissionViewReq(
            PermConstants.TargetType.USER, null, null, null, null, null, null, null, null, null,
            false, false, true, 20, 1, 50
        ));
        AbstractUser user = abstractUserMapper.selectValidById(userId, tenantId);
        if (user == null) {
            return null;
        }
        String subjectTypeCode = typeResolutionService.resolveTypeCode(tenantId, "user_type", user.getUserType());
        if (subjectTypeCode == null) {
            subjectTypeCode = PermConstants.TargetType.USER;
        }
        return new UserPermissionViewResp(subjectTypeCode, user.getExternalId(), user.getName(), paged.items());
    }

    /**
     * 获取有效权限列表
     * <p>
     * 查询用户或角色的有效权限。对于用户，查询其通过角色获得的权限；
     * 对于角色，查询其直接授予的权限。需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限视图请求，包含目标类型、用户/角色标识、过滤条件、分页参数
     * @return 有效权限响应，包含权限列表和分页信息
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public PermissionEffectivePermissionsResp getEffectivePermissions(Long tenantId, UserPermissionViewReq req) {
        // 权限校验：查看权限配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

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

    /**
     * 获取用户权限视图（带过滤条件）
     * <p>
     * 六阶段处理流程：
     * 1. prepareContext：准备上下文，加载用户信息和分页参数
     * 2. loadRoles：加载并过滤用户角色
     * 3. loadPermissions：加载权限及相关实体（资源、操作、业务域）
     * 4. filterPermissions：根据请求条件过滤权限
     * 5. paginateResults：分页结果并按资源分组
     * 6. assembleResponse：组装最终响应
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param req      权限视图请求，包含过滤条件和分页参数
     * @return 分页的权限视图响应
     */
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
     * 阶段1：准备上下文
     * <p>
     * 加载用户信息、设置分页参数、解析业务域ID。
     * </p>
     *
     * @param context 权限查询上下文
     */
    private void prepareContext(PermissionQueryContext context) {
        context.setPageNum(PageUtil.pageNum(context.getRequest().pageNum()));
        context.setPageSize(PageUtil.pageSize(context.getRequest().pageSize()));
        context.setOffset(Math.max((context.getPageNum() - 1) * context.getPageSize(), 0));

        AbstractUser user = abstractUserMapper.selectValidById(context.getUserId(), context.getTenantId());
        context.setUser(user);

        if (user != null) {
            Long domainId = typeResolutionService.resolveDomainId(context.getTenantId(), context.getRequest().domainCode());
            context.setDomainId(domainId);
        }
    }

    /**
     * 阶段2：加载并过滤用户角色
     * <p>
     * 解析用户的有效角色，根据请求条件过滤角色ID，
     * 批量加载角色实体信息。
     * </p>
     *
     * @param context 权限查询上下文
     */
    private void loadRoles(PermissionQueryContext context) {
        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(
            context.getTenantId(), context.getUserId());
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
     * 阶段3：加载权限及相关实体
     * <p>
     * 批量加载角色资源权限、操作权限、资源实体、业务域编码。
     * 批量解析资源类型编码和角色类型编码，避免N+1查询。
     * </p>
     *
     * @param context 权限查询上下文
     */
    private void loadPermissions(PermissionQueryContext context) {
        List<RoleResourcePermission> allPerms = rolePermMapper.selectValidByRoleIds(
            context.getTenantId(), context.getFilteredRoleIds());
        context.setAllPermissions(allPerms);

        if (allPerms.isEmpty()) {
            return;
        }

        Set<Long> resourceIds = allPerms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(context.getTenantId(), resourceIds);
        context.setResourceMap(resourceMap);

        // 批量解析资源类型编码
        Set<Integer> allResourceTypes = allPerms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        allResourceTypes.addAll(resourceMap.values().stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
        context.setOperationMap(loadOperationsByResourceTypes(context.getTenantId(), allResourceTypes));
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(
            context.getTenantId(), "resource_type", allResourceTypes);
        context.setResourceTypeCodeMap(resourceTypeCodeMap);

        Map<Long, String> domainCodeMap = buildResourceDomainCodeMap(
            context.getTenantId(), resourceMap.values(), resourceTypeCodeMap);
        context.setDomainCodeMap(domainCodeMap);

        // 解析API类型值用于过滤
        Integer apiTypeValue = typeResolutionService.resolveTypeValue(context.getTenantId(), "resource_type", ResourceTypeCode.API);
        context.setApiTypeValue(apiTypeValue);

        // 批量解析角色类型编码
        Set<Integer> roleTypeValues = context.getRoleMap().values().stream()
            .map(AbstractRole::getRoleType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> roleTypeCodeMap = typeResolutionService.batchResolveTypeCodes(
            context.getTenantId(), "role_type", roleTypeValues);
        context.setRoleTypeCodeMap(roleTypeCodeMap);
    }

    /**
     * 阶段4：过滤权限
     * <p>
     * 根据请求条件过滤权限：作用域、操作码、资源类型、资源关键词、
     * API资源、业务域等。
     * </p>
     *
     * @param context 权限查询上下文
     */
    private void filterPermissions(PermissionQueryContext context) {
        List<RoleResourcePermission> filteredPerms = context.getAllPermissions().stream()
            .filter(p -> matchesFilters(p, context))
            .toList();
        context.setFilteredPermissions(filteredPerms);
    }

    /**
     * 阶段5：分页结果并按资源分组
     * <p>
     * 对过滤后的权限进行分页，按资源实体ID分组。
     * </p>
     *
     * @param context 权限查询上下文
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
     * 阶段6：组装最终响应
     * <p>
     * 将分页后的权限数据转换为ResourcePermissionView响应对象。
     * 构建操作码列表、来源角色列表、匹配权限ID列表。
     * </p>
     *
     * @param context 权限查询上下文
     * @return 分页的权限视图响应
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
     * 检查权限是否匹配过滤条件
     * <p>
     * 根据请求条件检查权限是否匹配：
     * - 作用域过滤：是否包含作用域权限
     * - 操作码过滤：操作码是否在指定列表中
     * - 资源类型过滤：资源类型是否在指定列表中
     * - 资源关键词过滤：资源名称是否包含关键词
     * - API资源过滤：是否排除API资源
    * - 业务域过滤：资源类型是否命中当前域分类范围
     * </p>
     *
     * @param perm    角色资源权限
     * @param context 权限查询上下文
     * @return 是否匹配过滤条件
     */
    private boolean matchesFilters(RoleResourcePermission perm, PermissionQueryContext context) {
        // 作用域过滤
        if (!context.shouldIncludeScopes() && perm.getDependOn() != null) {
            return false;
        }

        // 操作码过滤
        if (context.hasOperationCodesFilter()) {
            OperationPermission op = findGrantedOperation(context.getOperationMap(), perm.getResourceType(), perm.getGrantedBits());
            if (op == null || op.getCode() == null || !context.getOperationCodesFilter().contains(op.getCode())) {
                return false;
            }
        }

        ResourceEntity resource = perm.getResourceEntityId() == null
            ? null : context.getResourceMap().get(perm.getResourceEntityId());

        // 非scopeAll必须有资源
        if (resource == null && !Boolean.TRUE.equals(perm.getScopeAll())) {
            return false;
        }

        if (resource != null) {
            // 资源类型过滤
            String resourceTypeCode = context.getResourceTypeCodeMap().get(resource.getResourceType());
            if (context.hasResourceTypeCodesFilter()
                && (resourceTypeCode == null || !context.getRequest().resourceTypeCodes().contains(resourceTypeCode))) {
                return false;
            }

            // 资源关键词过滤
            if (context.hasResourceKeywordFilter()
                && (resource.getName() == null || !resource.getName().contains(context.getRequest().resourceKeyword()))) {
                return false;
            }

            // API资源过滤
            if (!context.shouldIncludeApiResources() && context.getApiTypeValue() != null
                && Objects.equals(context.getApiTypeValue(), resource.getResourceType())) {
                return false;
            }

            // 业务域过滤 - 基于资源类型码判断
            if (context.hasDomainFilter()) {
                if (resourceTypeCode == null || !domainClassifyService.matchesTypeCode(
                    context.getTenantId(), DomainQueryMode.GLOBAL_PLUS,
                    context.getRequest().domainCode(), resourceTypeCode)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 构建单个资源的权限视图
     * <p>
     * 将资源的多个权限记录合并为一个视图对象。
     * 提取操作码列表、来源角色列表（可选截断）、匹配权限ID列表。
     * </p>
     *
     * @param resourceId        资源ID
     * @param perms             资源相关的权限列表
     * @param resource          资源实体
     * @param context           权限查询上下文
     * @param sourceRoleLimit   来源角色数量限制
     * @param includeSourceRoles 是否包含来源角色
     * @return 资源权限视图
     */
    private ResourcePermissionView buildResourcePermissionView(
            Long resourceId,
            List<RoleResourcePermission> perms,
            ResourceEntity resource,
            PermissionQueryContext context,
            int sourceRoleLimit,
            boolean includeSourceRoles) {

        Set<String> operationCodes = perms.stream()
            .map(RoleResourcePermission::getGrantedBits)
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
            context.getDomainCodeMap().get(resourceId),
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
     * 创建空响应
     * <p>
     * 用于早期退出场景（用户不存在、无角色、无权限）。
     * </p>
     *
     * @param context 权限查询上下文
     * @return 空的分页响应
     */
    private PaginatedResp<ResourcePermissionView> emptyResponse(PermissionQueryContext context) {
        return new PaginatedResp<>(List.of(), 0, context.getPageNum(), context.getPageSize(), false);
    }

    /**
     * 批量加载角色
     * <p>
     * 根据角色ID集合批量查询角色实体。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 角色ID到角色实体的映射
     */
    private Map<Long, AbstractRole> loadRoles(Long tenantId, Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return abstractRoleMapper.selectValidByIds(tenantId, roleIds).stream()
            .collect(Collectors.toMap(AbstractRole::getId, role -> role));
    }

    /**
     * 批量加载操作权限
     * <p>
     * 根据操作权限ID集合批量查询操作权限实体。
     * </p>
     *
     * @param operationIds 操作权限ID集合
     * @return 操作权限ID到操作权限实体的映射
     */
    private Map<Long, OperationPermission> loadOperations(Long tenantId, Set<Long> operationIds) {
        if (operationIds.isEmpty()) {
            return Map.of();
        }
        return entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);
    }

    private Map<Long, OperationPermission> loadOperationsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Map.of();
        }
        return entityBatchLoadDomainService.batchLoadOperationsByResourceTypes(tenantId, resourceTypes)
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(Collectors.toMap(OperationPermission::getId, op -> op, (left, _unused) -> left, LinkedHashMap::new));
    }

    private OperationPermission findGrantedOperation(Map<Long, OperationPermission> operationMap, Integer resourceType, Long grantedBits) {
        return OperationPermissionUtils.findByResourceTypeAndBinaryBit(operationMap, resourceType, grantedBits);
    }

    /**
     * 批量加载资源实体
     * <p>
     * 根据资源ID集合批量查询资源实体。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceIds 资源ID集合
     * @return 资源ID到资源实体的映射
     */
    private Map<Long, ResourceEntity> loadResources(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return entityBatchLoadDomainService.batchLoadResources(tenantId, resourceIds);
    }

    /**
     * 按资源类型码反查每个资源所属的业务域编码
     * <p>
     * 资源实体不再直接存储bizDomainId，因此这里根据resourceTypeCode反查域配置。
     * </p>
     *
     * @param tenantId             租户ID
     * @param resources            资源实体集合
     * @param resourceTypeCodeMap  资源类型值到资源类型码的映射
     * @return 资源ID到业务域编码的映射
     */
    private Map<Long, String> buildResourceDomainCodeMap(Long tenantId,
                                                         Collection<ResourceEntity> resources,
                                                         Map<Integer, String> resourceTypeCodeMap) {
        if (resources.isEmpty() || resourceTypeCodeMap.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> domainIdByTypeCode = new HashMap<>();
        for (ResourceEntity resource : resources) {
            String resourceTypeCode = resourceTypeCodeMap.get(resource.getResourceType());
            if (resourceTypeCode == null || domainIdByTypeCode.containsKey(resourceTypeCode)) {
                continue;
            }
            domainIdByTypeCode.put(resourceTypeCode,
                domainClassifyService.findDomainIdByTypeCode(tenantId, resourceTypeCode));
        }

        Set<Long> domainIds = domainIdByTypeCode.values().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> domainCodeById = loadDomainCodes(tenantId, domainIds);

        Map<Long, String> resourceDomainCodeMap = new HashMap<>();
        for (ResourceEntity resource : resources) {
            String resourceTypeCode = resourceTypeCodeMap.get(resource.getResourceType());
            Long domainId = resourceTypeCode != null ? domainIdByTypeCode.get(resourceTypeCode) : null;
            if (domainId != null && domainCodeById.containsKey(domainId)) {
                resourceDomainCodeMap.put(resource.getId(), domainCodeById.get(domainId));
            }
        }
        return resourceDomainCodeMap;
    }

    /**
     * 批量加载业务域编码
     * <p>
        * 根据业务域ID集合批量查询业务域编码。
     * </p>
     *
     * @param tenantId  租户ID
     * @param domainIds 业务域ID集合
     * @return 业务域ID到业务域编码的映射
     */
    private Map<Long, String> loadDomainCodes(Long tenantId, Set<Long> domainIds) {
        if (domainIds.isEmpty()) {
            return Map.of();
        }
        return bizDomainMapper.selectValidByIds(tenantId, domainIds).stream()
            .collect(Collectors.toMap(BizDomain::getId, BizDomain::getCode));
    }

    /**
     * 过滤角色ID
     * <p>
     * 根据角色外部ID、角色类型、业务域等条件过滤角色ID集合。
     * </p>
     *
     * @param tenantId           租户ID
     * @param roleIds            角色ID集合
     * @param sourceRoleExternalId 来源角色外部ID，可选
     * @param roleTypeCode       角色类型编码，可选
    * @param domainCode         业务域编码，可选，仅用于保持调用签名兼容
     * @return 过滤后的角色ID集合
     */
    private Set<Long> filterRoleIds(Long tenantId, Set<Long> roleIds, String sourceRoleExternalId, String roleTypeCode, String domainCode) {
        if ((sourceRoleExternalId == null || sourceRoleExternalId.isBlank()) && (roleTypeCode == null || roleTypeCode.isBlank())) {
            return roleIds;
        }
        Integer roleTypeValue = roleTypeCode == null || roleTypeCode.isBlank()
            ? null : typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
        return abstractRoleMapper.selectFilteredByIds(tenantId, roleIds,
            sourceRoleExternalId, roleTypeValue).stream()
            .map(AbstractRole::getId).collect(Collectors.toSet());
    }

    /**
     * 获取资源权限视图
     * <p>
     * 查询指定资源的权限授予信息，返回资源编码、名称及授予该资源的角色列表。
     * 需要RESOURCE_VIEW权限。批量加载角色和操作权限避免N+1查询。
     * </p>
     *
     * @param tenantId        租户ID
     * @param domainCode      业务域编码，可选
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode    资源编码
     * @param codeType        编码类型，可选
     * @return 资源权限视图响应，资源不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public ResourcePermissionViewResp getResourcePermissions(Long tenantId, String domainCode, String resourceTypeCode, String resourceCode, String codeType) {
        // 权限校验：查看资源需要RESOURCE_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        Long resourceEntityId = typeResolutionService.resolveResourceId(tenantId, resourceTypeCode, resourceCode, codeType, domainCode);
        if (resourceEntityId != null) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceEntityId, OperationCodeConstants.VIEW);
        } else {
            // 资源不存在时使用类型级别权限校验
            engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.VIEW);
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

        // 批量加载角色避免N+1查询
        Map<Long, AbstractRole> roleMap = loadRoles(tenantId, byRole.keySet());

        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = loadOperationsByResourceTypes(tenantId, resourceTypeValues);

        // 批量解析角色类型编码（避免N+1）
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
                    OperationPermission op = findGrantedOperation(opMap, p.getResourceType(), p.getGrantedBits());
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
     * 获取角色权限视图
     * <p>
     * 查询指定角色的权限授予信息，返回角色名称及权限列表。
     * 需要ROLE_VIEW权限。批量加载资源实体和操作权限避免N+1查询。
     * </p>
     *
     * @param tenantId    租户ID
     * @param domainCode  业务域编码，可选
     * @param roleTypeCode 角色类型编码
     * @param roleExternalId 角色外部ID
     * @param expandSub   是否展开子角色（当前未实现）
     * @return 角色权限视图响应，角色不存在返回null
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public RolePermissionViewResp getRolePermissions(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, boolean expandSub) {
        // 权限校验：查看角色需要ROLE_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        Long roleId = typeResolutionService.resolveRoleId(tenantId, roleTypeCode, roleExternalId, domainCode);
        if (roleId != null) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.VIEW);
        } else {
            // 角色不存在时使用类型级别权限校验
            engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW);
        }

        if (roleId == null) {
            return null;
        }
        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        if (role == null) return null;

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleId(tenantId, roleId);

        // 批量加载资源实体避免N+1查询
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(tenantId, resourceIds);

        // 批量解析资源类型编码（避免N+1）
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);
        Map<Long, OperationPermission> opMap = loadOperationsByResourceTypes(tenantId, resourceTypeValues);

        List<PermissionItem> items = perms.stream()
            .map(p -> {
                ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
                OperationPermission op = findGrantedOperation(opMap, p.getResourceType(), p.getGrantedBits());
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

    /**
     * 分页获取角色权限列表
     * <p>
     * 分页查询指定角色的权限授予信息。
     * 需要ROLE_VIEW权限。批量加载资源实体和操作权限避免N+1查询。
     * </p>
     *
     * @param tenantId    租户ID
     * @param domainCode  业务域编码，可选
     * @param roleTypeCode 角色类型编码
     * @param roleExternalId 角色外部ID
     * @param pageNum     页码
     * @param pageSize    页大小
     * @return 分页的权限列表响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public PaginatedResp<PermissionItem> getRolePermissionItemsPaged(Long tenantId, String domainCode, String roleTypeCode, String roleExternalId, int pageNum, int pageSize) {
        // 权限校验：查看角色需要ROLE_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        Long roleId = typeResolutionService.resolveRoleId(tenantId, roleTypeCode, roleExternalId, domainCode);
        if (roleId != null) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.VIEW);
        } else {
            // 角色不存在时使用类型级别权限校验
            engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW);
        }

        if (roleId == null) {
            return new PaginatedResp<>(List.of(), 0L, pageNum, pageSize, false);
        }
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        long total = rolePermMapper.countByRoleId(tenantId, roleId);
        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleId(tenantId, roleId);
        // 批量加载资源实体避免N+1查询
        Set<Long> resourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> resourceMap = loadResources(tenantId, resourceIds);

        // 批量解析资源类型编码（避免N+1）
        Set<Integer> resourceTypeValues = perms.stream()
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);
        Map<Long, OperationPermission> opMap = loadOperationsByResourceTypes(tenantId, resourceTypeValues);

        List<PermissionItem> items = perms.stream().map(p -> {
            ResourceEntity resource = p.getResourceEntityId() == null ? null : resourceMap.get(p.getResourceEntityId());
            OperationPermission op = findGrantedOperation(opMap, p.getResourceType(), p.getGrantedBits());
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

    /**
     * 权限解释
     * <p>
     * 解释用户或角色对指定资源的权限状态。返回权限校验结果、
     * 来源角色列表（可选）、匹配权限ID列表、近期变更历史（可选）。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限解释请求，包含目标类型、用户/角色标识、资源标识、操作码等
     * @return 权限解释响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public PermissionExplainResp explain(Long tenantId, PermissionExplainReq req) {
        // 权限校验：解释权限配置需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

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
                    AbstractRole r = abstractRoleMapper.selectValidById(roleId, tenantId);
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
            PermissionRecentChangesReq recentReq = new PermissionRecentChangesReq(
                req.targetType(), req.subjectTypeCode(), req.subjectExternalId(),
                req.roleTypeCode(), req.roleExternalId(), req.domainCode(),
                now.minusDays(recentDays), now,
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

    /**
     * 获取权限变更历史
     * <p>
     * 查询用户或角色的权限变更历史记录。支持时间范围过滤和事件类型过滤。
     * 需要SYSTEM_CONFIG_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      变更历史请求，包含目标类型、用户/角色标识、时间范围、事件类型、分页参数
     * @return 变更历史响应，包含变更列表和分页信息
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public PermissionRecentChangesResp recentChanges(Long tenantId, PermissionRecentChangesReq req) {
        // 权限校验：查看变更日志需要SYSTEM_CONFIG_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.VIEW);

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

    /**
     * 获取用户有效角色列表
     * <p>
     * 查询用户当前有效的角色。需要USER_VIEW权限。
     * 通过用户权限视图提取来源角色信息。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      有效角色请求，包含用户类型、用户外部ID、业务域编码
     * @return 有效角色列表响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public ItemsResp<EffectiveRoleResp> listEffectiveRoles(Long tenantId, UserEffectiveRolesReq req) {
        // 权限校验：查看用户需要USER_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId != null) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW);
        } else {
            // 用户不存在时使用类型级别权限校验
            engine.validate(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.VIEW);
        }

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

    /**
     * 检查角色直接授予权限
     * <p>
     * 检查角色是否直接拥有对指定资源的指定操作权限。
     * 使用操作权限位运算进行匹配检查。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限解释请求
     * @return 权限校验响应
     */
    private AuthCheckResp checkRoleDirectGrant(Long tenantId, PermissionExplainReq req) {
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            return AuthCheckResp.deny("ROLE_NOT_FOUND");
        }
        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
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

        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleIdAndResourceId(
            tenantId, roleId, resourceEntityId);
        OperationPermission targetOp = operationPermissionMapper.selectOneById(operationPermissionId);
        if (targetOp == null || targetOp.getBinaryBit() == null || targetOp.getBinaryBit() == 0L) {
            return AuthCheckResp.deny("NO_PERMISSION");
        }

        Map<Long, OperationPermission> opMap = loadOperationsByResourceTypes(tenantId, Set.of(targetOp.getResourceType()));

        List<Long> matchedPermissionIds = new ArrayList<>();
        for (RoleResourcePermission perm : perms) {
            OperationPermission grantedOp = findGrantedOperation(opMap, perm.getResourceType(), perm.getGrantedBits());
            if (grantedOp == null) {
                continue;
            }
            if (OperationPermissionUtils.covers(grantedOp, targetOp)) {
                matchedPermissionIds.add(perm.getId());
            }
        }
        if (matchedPermissionIds.isEmpty()) {
            return AuthCheckResp.deny("NO_PERMISSION");
        }
        return AuthCheckResp.allow(List.of(roleId), matchedPermissionIds, false);
    }

    /**
     * 将变更日志转换为近期变更响应
     * <p>
     * 解析变更日志的JSON快照，提取事件类型、变更类型、权限键、来源角色等信息。
     * </p>
     *
     * @param log 变更日志响应
     * @return 近期变更响应
     */
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

    /**
     * 从JSON中解析文本值
     *
     * @param json JSON字符串
     * @param path JSON路径表达式
     * @return 解析的文本值，不存在返回null
     */
    private String parseText(String json, String path) {
        JsonNode node = parsePath(json, path);
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 从JSON中解析布尔值
     *
     * @param json JSON字符串
     * @param path JSON路径表达式
     * @return 解析的布尔值，不存在返回null
     */
    private Boolean parseBoolean(String json, String path) {
        JsonNode node = parsePath(json, path);
        return node == null || node.isNull() ? null : node.asBoolean();
    }

    /**
     * 从JSON中按路径解析节点
     * <p>
     * 支持点分隔路径和数组索引访问，如：items[0].changeType
     * </p>
     *
     * @param json JSON字符串
     * @param path JSON路径表达式
     * @return 解析的JsonNode，不存在返回null
     */
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

    /**
     * 获取用户资源权限树
     * <p>
     * 构建用户权限的资源树形结构。仅包含用户有权限的资源，
     * 并按父子关系组织成树形结构。需要USER_VIEW权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param req      资源树请求，包含用户类型、用户外部ID、业务域编码、资源类型、操作码等过滤条件
     * @return 资源权限树列表
     * @throws SecurityException 无权限时抛出
     */
    @Override
    public List<ResourcePermissionTreeResp> getUserResourceTree(Long tenantId, Long userId, UserResourceTreeReq req) {
        // 权限校验：查看用户需要USER_VIEW权限
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW);

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

    /**
     * 构建权限树节点
     * <p>
     * 递归构建资源权限树节点。包含资源信息、权限信息、子节点列表。
     * 使用visited集合防止循环引用。
     * </p>
     *
     * @param entityId   资源ID
     * @param viewMap    资源权限视图映射
     * @param entityMap  资源实体映射
     * @param childrenMap 子节点映射
     * @param tenantId   租户ID
     * @param visited    已访问节点集合（防止循环引用）
     * @return 资源权限树节点
     */
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