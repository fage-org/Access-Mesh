package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp.RoleTreeNode;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.RoleManageAppService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.TreeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final CacheService cacheService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final ObjectMapper objectMapper;
    private final AuditDomainService auditDomainService;
    private final PermQueryEngine engine;

    public RoleManageAppServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 SubjectDomainService subjectDomainService,
                                 CacheService cacheService,
                                 TypeResolutionService typeResolutionService,
                                 DomainClassifyService domainClassifyService,
                                 ObjectMapper objectMapper,
                                 AuditDomainService auditDomainService,
                                 PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.subjectDomainService = subjectDomainService;
        this.cacheService = cacheService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.objectMapper = objectMapper;
        this.auditDomainService = auditDomainService;
        this.engine = engine;
    }

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

        final Set<Long> roleIdsToEvictForCache = allIdsToDelete;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.evictBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleIdsToEvictForCache);
                }
            });
        }

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
    public List<RoleResp> listRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword, int offset, int limit) {
        Integer roleType = null;
        if (roleTypeCode != null && !roleTypeCode.isBlank()) {
            roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
        }
        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE);
        }
        if (!matchNone && roleTypeCode != null && !roleTypeCode.isBlank() && roleType == null) {
            matchNone = true;
        }
        return abstractRoleMapper.selectRoleListPaged(tenantId, roleType, keyword, matchNone, offset, limit)
            .stream().map(this::toRoleResp).collect(Collectors.toList());
    }

    @Override
    public long countRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword) {
        Integer roleType = null;
        if (roleTypeCode != null && !roleTypeCode.isBlank()) {
            roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
        }
        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.ROLE);
        }
        if (!matchNone && roleTypeCode != null && !roleTypeCode.isBlank() && roleType == null) {
            matchNone = true;
        }
        return abstractRoleMapper.selectRoleListCount(tenantId, roleType, keyword, matchNone);
    }

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