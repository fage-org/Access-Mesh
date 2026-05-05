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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;

@Service
public class RoleManageServiceImpl implements RoleManageService {

    private static final Logger log = LoggerFactory.getLogger(RoleManageServiceImpl.class);

    private final AbstractRoleMapper abstractRoleMapper;
    private final AbstractRoleDomainService abstractRoleDomainService;
    private final PermCacheDomainService permCacheDomainService;
    private final TypeResolutionService typeResolutionService;
    private final ObjectMapper objectMapper;
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final AuthorizationService authorizationService;
    private final PermQueryEngine engine;

    // TODO: 构造函数依赖过多(9个)，建议拆分角色CRUD和树形结构构建职责
    // 优先级：P3（低优先级，可关注但不强制整改）
    public RoleManageServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 AbstractRoleDomainService abstractRoleDomainService,
                                 PermCacheDomainService permCacheDomainService,
                                 TypeResolutionService typeResolutionService,
                                 ObjectMapper objectMapper,
                                 OperationLogDomainService operationLogDomainService,
                                 PermissionChangeDomainService permissionChangeDomainService,
                                 AuthorizationService authorizationService,
                                 PermQueryEngine engine) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.abstractRoleDomainService = abstractRoleDomainService;
        this.permCacheDomainService = permCacheDomainService;
        this.typeResolutionService = typeResolutionService;
        this.objectMapper = objectMapper;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
        this.authorizationService = authorizationService;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission check
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("No permission to create role");
        }

        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", req.roleTypeCode());
        if (roleType == null) {
            throw new IllegalArgumentException("Unknown roleTypeCode: " + req.roleTypeCode());
        }
        Long roleId = abstractRoleDomainService.createRole(
            tenantId, req.bizDomainId(), req.parentId(), roleType,
            req.externalId(), req.name(), req.sortOrder(), req.extra()
        );

        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        return toRoleResp(role);
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = abstractRoleDomainService.selectValidById(tenantId, roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }

        // Instance-level permission check: operator must have MANAGE permission on this specific role
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = abstractRoleDomainService.selectValidById(tenantId, roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }

        // Instance-level permission check: operator must have MANAGE permission on this specific role
        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

        if (parentId != null) {
            AbstractRole parent = abstractRoleDomainService.selectValidById(tenantId, parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Parent role not found: " + parentId);
            }
        }
        role.setParentId(parentId);
        role.setUpdatedBy(operatorId);
        role.setUpdatedAt(LocalDateTime.now());
        abstractRoleMapper.update(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long tenantId, Long roleId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        AbstractRole role = abstractRoleDomainService.selectValidById(tenantId, roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }

        // Instance-level permission check: operator must have MANAGE permission on this specific role
        engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

        abstractRoleDomainService.deleteRole(tenantId, roleId);

        // Invalidate caches（事务提交后执行）
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoles(Long tenantId, List<Long> roleIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        // Filter out null IDs
        Set<Long> validRoleIds = roleIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validRoleIds.isEmpty()) {
            return;
        }

        // Batch query roles to validate existence (use domain service, avoid N+1)
        List<AbstractRole> roles = abstractRoleDomainService.selectValidByIds(tenantId, validRoleIds);

        if (roles.isEmpty()) {
            return;
        }

        // Build map of existing roles
        Map<Long, AbstractRole> existingRoles = roles.stream()
            .collect(Collectors.toMap(AbstractRole::getId, r -> r));

        // Batch permission check - avoid N+1 queries
        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, existingRoles.keySet(), OperationCodeConstants.MANAGE);

        // Filter roles that operator has permission to delete
        Set<Long> permittedIds = new LinkedHashSet<>();
        for (Long roleId : existingRoles.keySet()) {
            if (!deniedIds.contains(roleId)) {
                permittedIds.add(roleId);
            } else {
                log.info("Operator {} denied to delete role: {}", operatorId, roleId);
            }
        }

        if (permittedIds.isEmpty()) {
            return;
        }

        // Collect all IDs to delete (including descendants of group roles)
        Set<Long> allIdsToDelete = new LinkedHashSet<>(permittedIds);

        // Find group roles and collect their descendants in batch
        Set<Long> groupRoleIds = permittedIds.stream()
            .filter(id -> {
                AbstractRole role = existingRoles.get(id);
                return role != null && role.getRoleType() != null
                    && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                        || role.getRoleType() == RoleType.ORG.getValue());
            })
            .collect(Collectors.toSet());

        if (!groupRoleIds.isEmpty()) {
            // Batch resolve all descendant IDs for group roles (1 query instead of N queries)
            List<Long> descendantIds = abstractRoleDomainService.resolveDescendantIdsBatch(tenantId, groupRoleIds);
            allIdsToDelete.addAll(descendantIds);
        }

        // Batch soft delete all roles (including descendants) - 1 UPDATE statement
        abstractRoleDomainService.softDeleteBatch(tenantId, allIdsToDelete);

        // Build audit log entries for permitted roles
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

        // Batch invalidate caches after transaction commit
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
                tenantId, null, operatorId, null, PermConstants.MaintainSource.MANUAL, "abstract-role-batch-remove"),
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
        operationLogDomainService.asyncRecord(
            "perm",
            "abstract-role-remove",
            "BATCH",
            tenantId,
            "soft-deleted " + allIdsToDelete.size() + " role(s) (including " + (allIdsToDelete.size() - permittedIds.size()) + " descendants), ids=" + permittedIds + ", denied=" + deniedIds.size(),
            operatorId,
            null,
            null,
            tenantId
        );
    }

    @Override
    public List<RoleTreeResp> getRoleTree(Long tenantId, String domainCode) {
        Long bizDomainId = null;
        if (domainCode != null && !domainCode.isBlank()) {
            bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (bizDomainId == null) {
                throw new IllegalArgumentException("Unknown domainCode: " + domainCode);
            }
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.STATUS.eq(PermissionConstants.ENABLED_STATUS));
        if (bizDomainId != null) {
            qw.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(bizDomainId).or(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()));
        } else {
            qw.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull());
        }

        List<AbstractRole> allRoles = abstractRoleMapper.selectListByQuery(qw);

        // Build tree using TreeBuilder
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
        QueryWrapper queryWrapper = buildRoleListQuery(tenantId, domainCode, roleTypeCode, keyword)
            .limit(limit)
            .offset(offset);
        return abstractRoleMapper.selectListByQuery(queryWrapper)
            .stream().map(this::toRoleResp).collect(Collectors.toList());
    }

    @Override
    public long countRoles(Long tenantId, String domainCode, String roleTypeCode, String keyword) {
        return abstractRoleMapper.selectCountByQuery(
            buildRoleListQuery(tenantId, domainCode, roleTypeCode, keyword)
        );
    }

    private QueryWrapper buildRoleListQuery(Long tenantId, String domainCode, String roleTypeCode, String keyword) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0));
        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return queryWrapper.and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            queryWrapper.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId).or(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()));
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

    private RoleResp toRoleResp(AbstractRole role) {
        String roleTypeName = RoleType.safeGetLabel(role.getRoleType());

        return new RoleResp(
            role.getId(), role.getTenantId(), role.getBizDomainId(),
            role.getParentId(), typeResolutionService.resolveTypeCode(role.getTenantId(), "role_type", role.getRoleType()), roleTypeName,
            role.getExternalId(), role.getName(), role.getStatus(),
            role.getSortOrder(), role.getExtra(),
            role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}
