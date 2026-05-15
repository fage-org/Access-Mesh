package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp.RoleTreeNode;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.RoleManageService;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.SqlUtil;
import cn.ac.fage.accessmesh.permission.util.TreeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;

/**
 * 角色管理服务实现类
 * <p>
 * 提供角色的CRUD操作、树结构查询、角色移动等功能。
 * 角色是权限系统的核心概念，用于组织用户并配置权限。
 * 支持组角色、组织角色、业务角色等多种类型。
 * 所有操作均进行权限校验，确保操作者有相应权限。
 * </p>
 */
@Service
public class RoleManageServiceImpl implements RoleManageService {

    private static final Logger log = LoggerFactory.getLogger(RoleManageServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final AbstractRoleDomainService abstractRoleDomainService;
    private final PermCacheDomainService permCacheDomainService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final ObjectMapper objectMapper;
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final AuthorizationService authorizationService;
    private final PermQueryEngine engine;

    // TODO: 构造函数依赖过多(9个)，建议拆分角色CRUD和树形结构构建职责
    // 优先级：P3（低优先级，可关注但不强制整改）

    /**
     * 构造函数注入依赖
     *
     * @param abstractRoleMapper           抽象角色Mapper
     * @param abstractRoleDomainService    抽象角色领域服务
     * @param permCacheDomainService       权限缓存领域服务
     * @param typeResolutionService        类型解析服务
     * @param objectMapper                 JSON映射器
     * @param operationLogDomainService    操作日志领域服务
     * @param permissionChangeDomainService 权限变更领域服务
     * @param authorizationService         授权服务
     * @param engine                       权限查询引擎
     */
    public RoleManageServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 AbstractRoleDomainService abstractRoleDomainService,
                                 PermCacheDomainService permCacheDomainService,
                                 TypeResolutionService typeResolutionService,
                                 DomainClassifyService domainClassifyService,
                                 ObjectMapper objectMapper,
                                 OperationLogDomainService operationLogDomainService,
                                 PermissionChangeDomainService permissionChangeDomainService,
                                 AuthorizationService authorizationService,
                                 PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.abstractRoleDomainService = abstractRoleDomainService;
        this.permCacheDomainService = permCacheDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.objectMapper = objectMapper;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
        this.authorizationService = authorizationService;
        this.engine = engine;
    }

    /**
     * 创建角色
     * <p>
     * 在指定租户和域下创建新角色。
     * 需要CREATE权限才能执行此操作。
     * 角色类型可以是组角色、组织角色、业务角色等。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        角色创建请求，包含角色基本信息
     * @param operatorId 操作者ID，可选，默认为系统操作
     * @return 创建成功的角色详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限检查：需要有角色的CREATE权限
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无创建角色的权限");
        }

        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", req.roleTypeCode());
        if (roleType == null) {
            throw new IllegalArgumentException("未知的roleTypeCode: " + req.roleTypeCode());
        }
        Long roleId = abstractRoleDomainService.createRole(
            tenantId, req.parentId(), roleType,
            req.externalId(), req.name(), req.sortOrder(), req.extra()
        );

        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        return toRoleResp(role);
    }

    /**
     * 获取角色详情
     * <p>
     * 根据角色ID查询角色的完整信息。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色详情，不存在返回null
     */
    @Override
    public RoleResp getRole(Long tenantId, Long roleId) {
        AbstractRole role = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(roleId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        return role != null ? toRoleResp(role) : null;
    }

    /**
     * 更新角色信息
     * <p>
     * 更新角色的名称、状态、排序顺序等属性。
     * 需要有对该角色的MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param name       角色名称，可选
     * @param status     状态，可选
     * @param sortOrder  排序顺序，可选
     * @param extra      扩展属性，可选
     * @param operatorId 操作者ID，可选
     * @return 更新后的角色详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = abstractRoleDomainService.selectValidById(tenantId, roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }

        // 实例级权限检查：操作者需要对该角色有MANAGE权限
        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

        if (name != null) role.setName(name);
        if (status != null) role.setStatus(status);
        if (sortOrder != null) role.setSortOrder(sortOrder);
        if (extra != null) role.setExtra(extra);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        abstractRoleMapper.update(role);

        return toRoleResp(role);
    }

    /**
     * 移动角色到新的父角色下
     * <p>
     * 调整角色在树结构中的位置。
     * 需要有对该角色的MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param parentId   目标父角色ID，null表示移至根级
     * @param operatorId 操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = abstractRoleDomainService.selectValidById(tenantId, roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }

        // 实例级权限检查
        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

        if (parentId != null) {
            AbstractRole parent = abstractRoleDomainService.selectValidById(tenantId, parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父角色不存在: " + parentId);
            }
        }
        role.setParentId(parentId);
        role.setUpdatedBy(operatorId);
        role.setUpdatedAt(LocalDateTime.now());
        abstractRoleMapper.update(role);
    }

    /**
     * 删除单个角色
     * <p>
     * 软删除指定角色。
     * 需要有对该角色的MANAGE权限。
     * 删除后在事务提交后失效缓存。
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param operatorId 操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long tenantId, Long roleId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = abstractRoleDomainService.selectValidById(tenantId, roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleId);
        }

        // 实例级权限检查
        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

        abstractRoleDomainService.deleteRole(tenantId, roleId);

        // 失效缓存（事务提交后执行）
        final Long roleIdForCache = roleId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permCacheDomainService.evictRolePermSnapshot(tenantId, roleIdForCache);
                }
            });
        }
    }

    /**
     * 批量删除角色及其所有子孙角色
     * <p>
     * 批量软删除角色及其所有子孙角色。
     * 使用批量查询优化避免N+1问题。
     * 仅删除有MANAGE权限的角色，无权限的角色会被跳过。
     * 组角色和组织角色删除时会级联删除其子孙角色。
     * </p>
     *
     * @param tenantId  租户ID
     * @param roleIds   待删除的角色ID列表
     * @param operatorId 操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoles(Long tenantId, List<Long> roleIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validRoleIds = roleIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validRoleIds.isEmpty()) {
            return;
        }

        // 批量查询角色验证存在性（使用领域服务，避免N+1）
        List<AbstractRole> roles = abstractRoleDomainService.selectValidByIds(tenantId, validRoleIds);

        if (roles.isEmpty()) {
            return;
        }

        // 构建已存在角色映射
        Map<Long, AbstractRole> existingRoles = roles.stream()
            .collect(Collectors.toMap(AbstractRole::getId, r -> r));

        // 批量权限检查 - 避免N+1查询
        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, existingRoles.keySet(), OperationCodeConstants.MANAGE);

        // 过滤有权限删除的角色
        Set<Long> permittedIds = new LinkedHashSet<>();
        for (Long roleId : existingRoles.keySet()) {
            if (!deniedIds.contains(roleId)) {
                permittedIds.add(roleId);
            } else {
                log.info("操作者{}无权删除角色: {}", operatorId, roleId);
            }
        }

        if (permittedIds.isEmpty()) {
            return;
        }

        // 收集所有待删除ID（包括组角色的子孙）
        Set<Long> allIdsToDelete = new LinkedHashSet<>(permittedIds);

        // 找出组角色并批量收集其子孙
        Set<Long> groupRoleIds = permittedIds.stream()
            .filter(id -> {
                AbstractRole role = existingRoles.get(id);
                return role != null && role.getRoleType() != null
                    && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                        || role.getRoleType() == RoleType.ORG.getValue());
            })
            .collect(Collectors.toSet());

        if (!groupRoleIds.isEmpty()) {
            // 批量解析组角色的所有子孙ID（1次查询替代N次查询）
            List<Long> descendantIds = abstractRoleDomainService.resolveDescendantIdsBatch(tenantId, groupRoleIds);

            // Check MANAGE permission on descendant roles too
            Set<Long> descendantSet = new HashSet<>(descendantIds);
            Set<Long> deniedDescendantIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, descendantSet, OperationCodeConstants.MANAGE);
            for (Long descId : descendantIds) {
                if (!deniedDescendantIds.contains(descId)) {
                    allIdsToDelete.add(descId);
                } else {
                    log.info("操作者{}无权删除子孙角色: {}", operatorId, descId);
                }
            }
        }

        // 批量软删除所有角色（包括子孙） - 1条UPDATE语句
        abstractRoleDomainService.softDeleteBatch(tenantId, allIdsToDelete);

        // 构建审计日志条目
        ArrayNode itemsJson = objectMapper.createArrayNode();
        for (Long roleId : permittedIds) {
            AbstractRole role = existingRoles.get(roleId);
            ObjectNode it = objectMapper.createObjectNode();
            it.put("changeType", "REMOVE");
            ObjectNode roleNode = it.putObject("role");
            roleNode.put("roleTypeCode", typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()));
            roleNode.put("roleExternalId", role.getExternalId());
            roleNode.put("roleName", role.getName() != null ? role.getName() : "");
            itemsJson.add(it);
        }

        // 事务提交后批量失效缓存
        final Set<Long> roleIdsToEvictForCache = allIdsToDelete;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long roleId : roleIdsToEvictForCache) {
                        permCacheDomainService.evictRolePermSnapshot(tenantId, roleId);
                    }
                }
            });
        }

        // 记录变更日志
        ObjectNode diffRoot = objectMapper.createObjectNode();
        diffRoot.put("eventType", "ROLE_BATCH_DELETE");
        diffRoot.set("items", itemsJson);
        String diffSnapshot;
        try {
            diffSnapshot = objectMapper.writeValueAsString(diffRoot);
        } catch (Exception e) {
            diffSnapshot = "{}";
        }
        Long[] roleArr = permittedIds.toArray(Long[]::new);
        permissionChangeDomainService.record(
            new PermissionChangeDomainService.ChangeLogContext(
                tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "abstract-role-batch-remove"),
            List.of(new PermissionChangeDomainService.ChangeLogEntry(
                "abstract_role",
                0L,
                "BATCH_DELETE",
                null,
                null,
                diffSnapshot,
                new Long[0],
                roleArr
            ))
        );

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "abstract-role-remove",
            "BATCH",
            tenantId,
            "软删除了" + allIdsToDelete.size() + "个角色（包括" + (allIdsToDelete.size() - permittedIds.size()) + "个子孙），ids=" + permittedIds + "，拒绝=" + deniedIds.size(),
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 查询角色树结构
     * <p>
     * 返回指定域下的角色层级树结构。
     * 用于前端展示角色组织关系。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 域编码，可选过滤条件
     * @return 角色树结构列表
     */
    @Override
    public List<RoleTreeResp> getRoleTree(Long tenantId, String domainCode) {
        if (domainCode != null && !domainCode.isBlank()
            && !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE)) {
            return List.of();
        }

        QueryWrapper qw = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.STATUS.eq(PermissionConstants.ENABLED_STATUS));

        List<AbstractRole> allRoles = abstractRoleMapper.selectListByQuery(qw);

        // 使用TreeBuilder构建树
        TreeBuilder<AbstractRole, RoleTreeNode> treeBuilder = new TreeBuilder<>(
            AbstractRole::getId,
            AbstractRole::getParentId,
            (role, children) -> new RoleTreeNode(
                role.getId(), role.getTenantId(), role.getParentId(),
                typeResolutionService.resolveTypeCode(role.getTenantId(), "role_type", role.getRoleType()),
                role.getName(), role.getExternalId(),
                role.getStatus(), role.getSortOrder(), children
            )
        );

        List<AbstractRole> roots = allRoles.stream()
            .filter(r -> r.getParentId() == null)
            .collect(Collectors.toList());

        return treeBuilder.buildTrees(roots, allRoles).stream()
            .map(RoleTreeResp::new)
            .collect(Collectors.toList());
    }

    /**
     * 分页查询角色列表
     * <p>
     * 支持按域、角色类型、关键字过滤，返回分页结果。
     * </p>
     *
     * @param tenantId      租户ID
     * @param domainCode    域编码，可选
     * @param roleTypeCode  角色类型编码，可选
     * @param keyword       搜索关键字，可选
     * @param offset        偏移量
     * @param limit         每页数量
     * @return 角色列表
     */
    @Override
    public List<RoleResp> listRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword, int offset, int limit) {
        QueryWrapper queryWrapper = buildRoleListQuery(tenantId, domainCode, roleTypeCode, keyword)
            .limit(limit)
            .offset(offset);
        return abstractRoleMapper.selectListByQuery(queryWrapper)
            .stream().map(this::toRoleResp).collect(Collectors.toList());
    }

    /**
     * 统计角色数量
     * <p>
     * 支持按域、角色类型、关键字过滤。
     * </p>
     *
     * @param tenantId      租户ID
     * @param domainCode    域编码，可选
     * @param roleTypeCode  角色类型编码，可选
     * @param keyword       搜索关键字，可选
     * @return 角色总数
     */
    @Override
    public long countRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword) {
        return abstractRoleMapper.selectCountByQuery(
            buildRoleListQuery(tenantId, domainCode, roleTypeCode, keyword)
        );
    }

    /**
     * 构建角色列表查询条件
     * <p>
     * 根据过滤条件构建QueryWrapper。
     * </p>
     *
     * @param tenantId      租户ID
     * @param domainCode    域编码
     * @param roleTypeCode  角色类型编码
     * @param keyword       搜索关键字
     * @return QueryWrapper查询条件
     */
    private QueryWrapper buildRoleListQuery(Long tenantId, String domainCode, String roleTypeCode, String keyword) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0));
        if (domainCode != null && !domainCode.isBlank()) {
            if (!domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE)) {
                return queryWrapper.and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
        }
        if (roleTypeCode != null && !roleTypeCode.isBlank()) {
            Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
            if (roleType == null) {
                return queryWrapper.and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            queryWrapper.and(AbstractRoleTableDef.ABSTRACT_ROLE.ROLE_TYPE.eq(roleType));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = SqlUtil.likePattern(keyword);
            queryWrapper.and(
                AbstractRoleTableDef.ABSTRACT_ROLE.NAME.like(pattern)
                    .or(AbstractRoleTableDef.ABSTRACT_ROLE.EXTERNAL_ID.like(pattern))
            );
        }
        return queryWrapper;
    }

    /**
     * 将角色实体转换为响应对象
     *
     * @param role 角色实体
     * @return 角色响应对象
     */
    private RoleResp toRoleResp(AbstractRole role) {
        String roleTypeName = RoleType.safeGetLabel(role.getRoleType());

        return new RoleResp(
            role.getId(), role.getTenantId(),
            role.getParentId(), typeResolutionService.resolveTypeCode(role.getTenantId(), "role_type", role.getRoleType()), roleTypeName,
            role.getExternalId(), role.getName(), role.getStatus(),
            role.getSortOrder(), role.getExtra(),
            role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}