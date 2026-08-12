package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.aop.PermissionChange;
import cn.ac.fage.accessmesh.access.permission.cache.PermissionChangeContext;
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
import java.util.Set;
import java.util.stream.Collectors;

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
public class RoleManageAppServiceImpl implements RoleManageAppService {

    private static final Logger log = LoggerFactory.getLogger(RoleManageAppServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final ObjectMapper objectMapper;
    private final AuditDomainService auditDomainService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param abstractRoleMapper    抽象角色数据访问层
     * @param subjectDomainService  主体领域服务
     * @param typeResolutionService 类型解析服务
     * @param domainClassifyService 域分类服务
     * @param objectMapper          JSON解析器
     * @param auditDomainService    审计领域服务
     * @param engine                权限查询引擎
     */
    public RoleManageAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 SubjectDomainService subjectDomainService,
                                 TypeResolutionService typeResolutionService,
                                 DomainClassifyService domainClassifyService,
                                 ObjectMapper objectMapper,
                                 AuditDomainService auditDomainService,
                                 PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.objectMapper = objectMapper;
        this.auditDomainService = auditDomainService;
        this.engine = engine;
    }

    /**
     * 创建角色
     * <p>
     * 创建新的角色实体。角色类型包括组角色、组织角色、业务角色等。
     * 可指定父角色实现角色的层级关系。
     * 需要ROLE_CREATE权限。
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
    @OperationLog(module = "perm", action = "abstract-role-create", targetType = "abstract_role", targetId = "#result.id()", summary = "'create role ' + #req.externalId()")
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无创建角色的权限");
        }

        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", req.roleTypeCode());
        if (roleType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "未知的roleTypeCode: " + req.roleTypeCode());
        }
        Long roleId = subjectDomainService.createRole(
            tenantId, req.parentId(), roleType,
            req.externalId(), req.name(), req.sortOrder(), req.extra()
        );

        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        return toRoleResp(role);
    }

    @Override
    public RoleResp getRole(Long tenantId, Long roleId) {
        AbstractRole role = abstractRoleMapper.selectValidById(roleId, tenantId);
        return role != null ? toRoleResp(role) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "abstract-role-update", targetType = "abstract_role", targetId = "#roleId", summary = "'update role ' + #roleId")
    public RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = subjectDomainService.selectValidRoleById(tenantId, roleId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "角色不存在: " + roleId);
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }

        if (name != null) role.setName(name);
        if (status != null) role.setStatus(status);
        if (sortOrder != null) role.setSortOrder(sortOrder);
        if (extra != null) role.setExtra(extra);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        abstractRoleMapper.update(role);

        return toRoleResp(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "abstract-role-move", targetType = "abstract_role", targetId = "#roleId", summary = "'move role ' + #roleId + ' to ' + #parentId")
    public void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = subjectDomainService.selectValidRoleById(tenantId, roleId);
        if (role == null) {
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "角色不存在: " + roleId);
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
        }

        if (parentId != null) {
            AbstractRole parent = subjectDomainService.selectValidRoleById(tenantId, parentId);
            if (parent == null) {
                throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "父角色不存在: " + parentId);
            }
        }
        role.setParentId(parentId);
        role.setUpdatedBy(operatorId);
        role.setUpdatedAt(LocalDateTime.now());
        abstractRoleMapper.update(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "abstract-role-remove", targetType = "BATCH", targetId = "", summary = "'batch remove roles'")
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

        Map<Long, AbstractRole> existingRoles = roles.stream()
            .collect(Collectors.toMap(AbstractRole::getId, r -> r));

        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, existingRoles.keySet(), OperationCodeConstants.MANAGE);

        Set<Long> permittedIds = new LinkedHashSet<>();
        for (Long roleId : existingRoles.keySet()) {
            if (!deniedIds.contains(roleId)) {
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

        Set<Long> groupRoleIds = permittedIds.stream()
            .filter(id -> {
                AbstractRole role = existingRoles.get(id);
                return role != null && role.getRoleType() != null
                    && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                        || role.getRoleType() == RoleType.ORG.getValue());
            })
            .collect(Collectors.toSet());

        if (!groupRoleIds.isEmpty()) {
            List<Long> descendantIds = subjectDomainService.resolveDescendantRoleIdsBatch(tenantId, groupRoleIds);

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

        subjectDomainService.softDeleteRoleBatch(tenantId, new java.util.HashSet<>(allIdsToDelete));
        OperationLogRuntimeContext.setSummary(
            "soft-deleted " + allIdsToDelete.size() + " role(s), rootPermitted="
                + permittedIds.size() + ", denied=" + deniedIds.size()
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

        // 登记需直清角色权限快照的角色，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
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
    public List<RoleTreeResp> getRoleTree(Long tenantId, String domainCode) {
        if (domainCode != null && !domainCode.isBlank()
            && !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE)) {
            return List.of();
        }

        List<AbstractRole> allRoles;
        if (domainCode != null && !domainCode.isBlank()) {
            boolean matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE);
            allRoles = abstractRoleMapper.selectEnabledRoleTree(tenantId);
        } else {
            allRoles = abstractRoleMapper.selectEnabledRoleTree(tenantId);
        }

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
        RoleTypeFilter roleTypeFilter = resolveRoleTypeFilter(tenantId, roleTypeCode, roleTypeCodes);
        boolean matchNone = roleTypeFilter.matchNone();
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = matchNone || !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE);
        }
        return abstractRoleMapper.selectRoleListCount(tenantId, roleTypeFilter.roleTypes(), keyword, matchNone);
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
