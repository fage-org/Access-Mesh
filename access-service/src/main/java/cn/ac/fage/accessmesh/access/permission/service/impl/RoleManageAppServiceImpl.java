package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleTreeResp.RoleTreeNode;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.RoleType;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.access.permission.util.TreeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现类
 * <p>
 * 提供角色的CRUD操作、树结构查询、角色移动等功能。
 * 角色是权限系统的核心概念，用于组织用户并配置权限。
 * T-PERM-043：create/update 显式拒绝 GROUP_ROLE（首期功能角色仅 BASIC_ROLE），
 * delete/move 保持可用作为存量 GROUP_ROLE 行的清理通道。
 * 所有操作均进行权限校验，确保操作者有相应权限。
 * </p>
 */
@Service
public class RoleManageAppServiceImpl implements RoleManageAppService {

    private static final Logger log = LoggerFactory.getLogger(RoleManageAppServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final ObjectMapper objectMapper;
    private final AuditDomainService auditDomainService;
    private final LocalProjectionGuard localProjectionGuard;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param abstractRoleMapper           抽象角色数据访问层
     * @param subjectDomainService         主体领域服务
     * @param typeResolutionService        类型解析服务
     * @param domainClassifyService        域分类服务
     * @param objectMapper                 JSON解析器
     * @param auditDomainService           审计领域服务
     * @param localProjectionDomainService 本地投影领域服务（ROLE 资源投影，T-ACCESS-019）
     * @param engine                       权限查询引擎
     */
    public RoleManageAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 SubjectDomainService subjectDomainService,
                                 TypeResolutionService typeResolutionService,
                                 DomainClassifyService domainClassifyService,
                                 ObjectMapper objectMapper,
                                 AuditDomainService auditDomainService,
                                 LocalProjectionGuard localProjectionGuard,
                                 LocalProjectionDomainService localProjectionDomainService,
                                 PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.objectMapper = objectMapper;
        this.auditDomainService = auditDomainService;
        this.localProjectionGuard = localProjectionGuard;
        this.localProjectionDomainService = localProjectionDomainService;
        this.engine = engine;
    }

    /**
     * 创建角色
     * <p>
     * 创建新的角色实体。T-PERM-043 后 ORG/POSITION 被 rejectReservedRoleType 拒（20045）、
     * GROUP_ROLE 显式拒（20022）；首期功能角色仅 BASIC_ROLE（PERSONAL 由用户同步连带
     * 生成，不归本入口管理，但入口未对其额外设限——历史行为）。
     * 可指定父角色实现角色的层级关系。需要ROLE_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含角色类型、外部ID、名称、父角色等
     * @param operatorId 操作者ID，可选
     * @return 创建的角色响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_ROLE_CREATE", targetType = "abstract_role", targetId = "#result.id()", summary = "'create role ' + #req.externalId()")
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无创建角色的权限");
        }
        localProjectionGuard.rejectReservedRoleType(req.roleTypeCode());

        // T-PERM-043：GROUP_ROLE 写入口收口——create 显式拒绝（20022 类型不匹配语义），
        // 首期功能角色仅 BASIC_ROLE；delete/move 保持可用，作为存量 GROUP_ROLE 行的清理通道
        if (PermConstants.TargetType.GROUP_ROLE.equals(req.roleTypeCode())) {
            throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                "不支持创建 GROUP_ROLE 分组角色（首期功能角色仅 BASIC_ROLE）");
        }

        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", req.roleTypeCode());
        if (roleType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "未知的roleTypeCode: " + req.roleTypeCode());
        }
        // 按值双保险：与 updateRole 同基准封死「自定义别名 type_code 映射 role_type=5」的
        // 理论绕过面（现实被 type_definition 唯一约束封死，此处防御纵深）
        if (roleType == RoleType.GROUP_ROLE.getValue()) {
            throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                "不支持创建 GROUP_ROLE 分组角色（首期功能角色仅 BASIC_ROLE）");
        }
        Long roleId = subjectDomainService.createRole(
            tenantId, req.parentId(), roleType,
            req.externalId(), req.name(), req.sortOrder(), req.extra()
        );

        // T-ACCESS-019：ROLE 资源投影与角色事实同事务（code=roleId，§12.3）；
        // 新角色无授权快照与成员，无需 PermissionChange 失效登记
        localProjectionDomainService.upsertRoleResource(
            tenantId, roleId, req.name(), 1, req.parentId());
        recordProjectionChange(tenantId, operatorId, roleId, "UPSERT");

        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        return toRoleResp(role);
    }

    @Override
    public RoleResp getRole(Long tenantId, String roleTypeCode, String roleExternalId) {
        // T-PERM-022 评审收口：读接口补类型级 ROLE:VIEW 门禁（与 /tree 同款；
        // list 信息量 >= tree，不设门禁会使 tree 门禁事实可绕）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE");
        }
        // T-PERM-022：业务键二元组定位（uk_abstract_role_external）；
        // 未知 roleTypeCode 不抛错，与 list 的空分页口径一致（查询语义，非写入校验）
        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
        if (roleType == null) {
            return null;
        }
        AbstractRole role = abstractRoleMapper.selectByTypeAndExternalId(tenantId, roleType, roleExternalId);
        return role != null ? toRoleResp(role) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_ROLE_UPDATE", targetType = "abstract_role", targetId = "#roleId", summary = "'update role ' + #roleId")
    public RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = subjectDomainService.selectValidRoleById(tenantId, roleId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "角色不存在: " + roleId);
        }
        localProjectionGuard.rejectIfLocalRole(role);

        // T-PERM-043：GROUP_ROLE 写入口收口——update 显式拒绝（20022 类型不匹配语义，
        // 请求体无 roleTypeCode，按目标角色现行类型判定）
        if (role.getRoleType() != null && role.getRoleType() == RoleType.GROUP_ROLE.getValue()) {
            throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                "不支持更新 GROUP_ROLE 分组角色（首期功能角色仅 BASIC_ROLE）");
        }

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }

        if (name != null) role.setName(name);
        if (status != null) role.setStatus(status);
        if (sortOrder != null) role.setSortOrder(sortOrder);
        if (extra != null) role.setExtra(extra);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        abstractRoleMapper.update(role);

        // T-ACCESS-019：ROLE 资源投影同事务镜像 name/status；status 禁用/启用影响有效角色解析，
        // 登记 markRoles 反查受影响用户失效（afterCommit 由 @PermissionChange AOP 处理）
        localProjectionDomainService.upsertRoleResource(
            tenantId, roleId, role.getName(), role.getStatus(), role.getParentId());
        recordProjectionChange(tenantId, operatorId, roleId, "UPSERT");
        PermissionChangeContext.markRoles(tenantId, Set.of(roleId));

        return toRoleResp(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_ROLE_MOVE", targetType = "abstract_role", targetId = "#roleId", summary = "'move role ' + #roleId + ' to ' + #parentId")
    public void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = subjectDomainService.selectValidRoleById(tenantId, roleId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "角色不存在: " + roleId);
        }
        localProjectionGuard.rejectIfLocalRole(role);

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }

        if (parentId != null) {
            AbstractRole parent = subjectDomainService.selectValidRoleById(tenantId, parentId);
            if (parent == null) {
                throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "父角色不存在: " + parentId);
            }
            // T-PERM-022：父子类型一致校验（同类型内嵌套合法，跨类型嵌套拒绝；
            // 前端拖拽 allowDrop 已拦截，此处为后端兜底）
            if (!Objects.equals(parent.getRoleType(), role.getRoleType())) {
                throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                    "不允许跨角色类型移动（父角色须与移动角色同类型）");
            }
            // T-PERM-022：环路防护——目标父为自身或其子孙时 parent 链成环
            // （环节点从树构建中静默消失、祖先/子孙递归 CTE 不收敛），对齐 admin 域先例
            if (roleId.equals(parentId)) {
                throw new BizException(PermissionErrorCode.ROLE_PARENT_INVALID.getCode(),
                    "父角色不能是自身或该角色的子孙: " + parentId);
            }
            List<Long> descendants = subjectDomainService.resolveDescendantRoleIdsBatch(tenantId, Set.of(roleId));
            if (descendants.contains(parentId)) {
                throw new BizException(PermissionErrorCode.ROLE_PARENT_INVALID.getCode(),
                    "父角色不能是自身或该角色的子孙: " + parentId);
            }
        }
        // 旧父链成员须在树变更前反查（提交后旧链关系不可再发现）；
        // 新父链成员由提交后 markRoles 反查覆盖（新树可达）
        Set<Long> oldTreeUserIds = subjectDomainService.findUserIdsByEffectiveRoles(tenantId, Set.of(roleId));
        role.setParentId(parentId);
        role.setUpdatedBy(operatorId);
        role.setUpdatedAt(LocalDateTime.now());
        abstractRoleMapper.update(role);

        // T-ACCESS-019：ROLE 资源投影同事务镜像父节点
        localProjectionDomainService.upsertRoleResource(
            tenantId, roleId, role.getName(), role.getStatus(), role.getParentId());
        recordProjectionChange(tenantId, operatorId, roleId, "UPSERT");
        PermissionChangeContext.markUsers(tenantId, oldTreeUserIds);
        PermissionChangeContext.markRoles(tenantId, Set.of(roleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_ROLE_REMOVE", targetType = "abstract_role", targetId = "", summary = "'batch remove roles'")
    @PermissionChange
    public void deleteRoles(Long tenantId, List<Long> roleIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (roleIds == null || roleIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validRoleIds = roleIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validRoleIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<AbstractRole> roles = subjectDomainService.selectValidRolesByIds(tenantId, validRoleIds);

        if (roles.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }
        roles.forEach(localProjectionGuard::rejectIfLocalRole);

        Map<Long, AbstractRole> existingRoles = roles.stream()
            .collect(Collectors.toMap(AbstractRole::getId, r -> r));

        // T-PERM-042：ROLE 实例门禁改业务编码语义（resource_entity(ROLE).code = roleId）
        Set<String> deniedRoleCodes = engine.getDeniedResourceCodes(
            tenantId, operatorId, ResourceTypeCode.ROLE,
            existingRoles.keySet().stream().map(String::valueOf).collect(Collectors.toSet()),
            OperationCodeConstants.MANAGE);

        Set<Long> permittedIds = new LinkedHashSet<>();
        for (Long roleId : existingRoles.keySet()) {
            if (!deniedRoleCodes.contains(String.valueOf(roleId))) {
                permittedIds.add(roleId);
            } else {
                log.info("操作者{}无权删除角色: {}", operatorId, roleId);
            }
        }

        if (permittedIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> allIdsToDelete = new LinkedHashSet<>(permittedIds);

        // 级联删除根：容器类型（GROUP_ROLE/ORG）与 BASIC_ROLE——同类型嵌套合法（T-PERM-022
        // 评审收口），删除有 BASIC 子级的 BASIC 角色若不级联会留悬挂子树（树上不可见无法再管理）
        Set<Long> cascadeRootIds = permittedIds.stream()
            .filter(id -> {
                AbstractRole role = existingRoles.get(id);
                return role != null && role.getRoleType() != null
                    && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                        || role.getRoleType() == RoleType.ORG.getValue()
                        || role.getRoleType() == RoleType.BASIC_ROLE.getValue());
            })
            .collect(Collectors.toSet());

        if (!cascadeRootIds.isEmpty()) {
            List<Long> descendantIds = subjectDomainService.resolveDescendantRoleIdsBatch(tenantId, cascadeRootIds);

            Set<Long> descendantSet = new HashSet<>(descendantIds);
            Set<String> deniedDescendantCodes = engine.getDeniedResourceCodes(
                tenantId, operatorId, ResourceTypeCode.ROLE,
                descendantSet.stream().map(String::valueOf).collect(Collectors.toSet()),
                OperationCodeConstants.MANAGE);
            for (Long descId : descendantIds) {
                if (!deniedDescendantCodes.contains(String.valueOf(descId))) {
                    allIdsToDelete.add(descId);
                } else {
                    log.info("操作者{}无权删除子孙角色: {}", operatorId, descId);
                }
            }
        }

        // 受影响用户须在软删前反查（提交后已删角色不可作组展开递归起点）
        Set<Long> affectedUserIds = subjectDomainService.findUserIdsByEffectiveRoles(tenantId, allIdsToDelete);
        subjectDomainService.softDeleteRoleBatch(tenantId, new java.util.HashSet<>(allIdsToDelete));
        // T-ACCESS-019：ROLE 资源投影同事务软删（含级联子孙角色），实例授权目标随之不可解析（fail-closed）
        localProjectionDomainService.softDeleteRoleResources(tenantId, allIdsToDelete);
        // 投影软删变更日志（覆盖含级联子孙的全量删除集合，与逐投影写登记口径对齐）
        List<AuditDomainService.ChangeLogEntry> projectionDeletes = allIdsToDelete.stream()
            .map(deletedId -> new AuditDomainService.ChangeLogEntry(
                "abstract_role", deletedId, "DELETE", null, null, null,
                new Long[0], new Long[]{deletedId}))
            .collect(Collectors.toList());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            projectionDeletes);
        OperationLogRuntimeContext.setSummary(
            "soft-deleted " + allIdsToDelete.size() + " role(s), rootPermitted="
            + permittedIds.size() + ", denied=" + deniedRoleCodes.size()
        );

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

        // 受影响用户按事务内预计算集合显式失效，角色权限快照直清照旧（铁律 P1-B）
        PermissionChangeContext.markUsers(tenantId, affectedUserIds);
        PermissionChangeContext.markRoleSnapshots(tenantId, allIdsToDelete);

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
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "abstract-role-batch-remove"),
            List.of(new AuditDomainService.ChangeLogEntry(
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
    }

    @Override
    public List<RoleTreeResp> getRoleTree(Long tenantId, String domainCode, boolean enabledOnly) {
        // T-PERM-042：授权页角色树读门禁（architecture §14.5 终态，类型级 ROLE:VIEW）
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE");
        }
        if (domainCode != null && !domainCode.isBlank()
            && !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE)) {
            return List.of();
        }

        // T-PERM-022：默认返回全部有效角色（含禁用）——status 仅作展示字段，禁用角色须在树中
        // 可见可再启用；授权页主体树等仅需启用态的消费方传 enabledOnly=true 由 SQL 过滤
        List<AbstractRole> allRoles = abstractRoleMapper.selectValidRoleTree(tenantId, enabledOnly);

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

    @Override
    public List<RoleResp> listRoles(Long tenantId, String domainCode, String roleTypeCode, List<String> roleTypeCodes, String keyword, int offset, int limit) {
        // T-PERM-022 评审收口：读接口补类型级 ROLE:VIEW 门禁（与 /tree 同款）
        Long viewOperatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, viewOperatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE");
        }
        RoleTypeFilter roleTypeFilter = resolveRoleTypeFilter(tenantId, roleTypeCode, roleTypeCodes);
        boolean matchNone = roleTypeFilter.matchNone();
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = matchNone || !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE);
        }
        return abstractRoleMapper.selectRoleListPaged(tenantId, roleTypeFilter.roleTypes(), keyword, matchNone, offset, limit)
            .stream().map(this::toRoleResp).collect(Collectors.toList());
    }

    @Override
    public long countRoles(Long tenantId, String domainCode, String roleTypeCode, List<String> roleTypeCodes, String keyword) {
        // T-PERM-022 评审收口：读接口补类型级 ROLE:VIEW 门禁（与 /tree 同款）
        Long countOperatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, countOperatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on ROLE");
        }
        RoleTypeFilter roleTypeFilter = resolveRoleTypeFilter(tenantId, roleTypeCode, roleTypeCodes);
        boolean matchNone = roleTypeFilter.matchNone();
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = matchNone || !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE);
        }
        return abstractRoleMapper.selectRoleListCount(tenantId, roleTypeFilter.roleTypes(), keyword, matchNone);
    }

    /** 投影写变更日志（T-ACCESS-019：ROLE 投影 UPSERT 与角色事实同事务登记）；
     * 操作者用方法已解析的 operatorId，不重读上下文（显式传参与上下文不一致时记错主体） */
    private void recordProjectionChange(Long tenantId, Long operatorId, Long roleId, String operation) {
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "abstract_role", roleId, operation, null, null, null,
                new Long[0], new Long[]{roleId})));
    }

    private RoleTypeFilter resolveRoleTypeFilter(Long tenantId, String roleTypeCode, List<String> roleTypeCodes) {
        Set<String> codes = new LinkedHashSet<>();
        if (roleTypeCode != null && !roleTypeCode.isBlank()) {
            codes.add(roleTypeCode.trim());
        }
        if (roleTypeCodes != null) {
            for (String code : roleTypeCodes) {
                if (code != null && !code.isBlank()) {
                    codes.add(code.trim());
                }
            }
        }
        if (codes.isEmpty()) {
            return new RoleTypeFilter(null, false);
        }

        Set<Integer> roleTypes = new LinkedHashSet<>();
        for (String code : codes) {
            Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", code);
            if (roleType == null) {
                return new RoleTypeFilter(Set.of(), true);
            }
            roleTypes.add(roleType);
        }
        return new RoleTypeFilter(roleTypes, roleTypes.isEmpty());
    }

    private record RoleTypeFilter(Set<Integer> roleTypes, boolean matchNone) {}

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
