package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp.RoleTreeNode;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.RoleManageService;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;

@Service
public class RoleManageServiceImpl implements RoleManageService {

    private final AbstractRoleMapper abstractRoleMapper;
    private final AbstractRoleDomainService abstractRoleDomainService;
    private final PermCacheDomainService permCacheDomainService;
    private final TypeResolutionService typeResolutionService;
    private final ObjectMapper objectMapper;
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;

    public RoleManageServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 AbstractRoleDomainService abstractRoleDomainService,
                                 PermCacheDomainService permCacheDomainService,
                                 TypeResolutionService typeResolutionService,
                                 ObjectMapper objectMapper,
                                 OperationLogDomainService operationLogDomainService,
                                 PermissionChangeDomainService permissionChangeDomainService) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.abstractRoleDomainService = abstractRoleDomainService;
        this.permCacheDomainService = permCacheDomainService;
        this.typeResolutionService = typeResolutionService;
        this.objectMapper = objectMapper;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
    }

    @Override
    @Transactional
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
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
                .where(ABSTRACT_ROLE.ID.eq(roleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        return role != null ? toRoleResp(role) : null;
    }

    @Override
    @Transactional
    public RoleResp updateRole(Long tenantId, Long roleId, String name, Integer status, Integer sortOrder, String extra, Long operatorId) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null || role.getDeleteFlag() != 0L || !role.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Role not found: " + roleId);
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
    @Transactional
    public void moveRole(Long tenantId, Long roleId, Long parentId, Long operatorId) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null || role.getDeleteFlag() != 0L || !tenantId.equals(role.getTenantId())) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }
        if (parentId != null) {
            AbstractRole parent = abstractRoleMapper.selectOneById(parentId);
            if (parent == null || parent.getDeleteFlag() != 0L || !tenantId.equals(parent.getTenantId())) {
                throw new IllegalArgumentException("Parent role not found: " + parentId);
            }
        }
        role.setParentId(parentId);
        role.setUpdatedBy(operatorId);
        role.setUpdatedAt(LocalDateTime.now());
        abstractRoleMapper.update(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long tenantId, Long roleId, Long operatorId) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null || role.getDeleteFlag() != 0L || !role.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }

        abstractRoleDomainService.deleteRole(tenantId, roleId);

        // Invalidate caches
        permCacheDomainService.evictRolePermSnapshot(tenantId, roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoles(Long tenantId, List<Long> roleIds, Long operatorId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> doneIds = new ArrayList<>();
        ArrayNode itemsJson = objectMapper.createArrayNode();
        for (Long roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            AbstractRole role = abstractRoleMapper.selectOneById(roleId);
            if (role == null || role.getDeleteFlag() != 0L || !tenantId.equals(role.getTenantId())) {
                throw new IllegalArgumentException("Role not found: " + roleId);
            }
            deleteRole(tenantId, roleId, operatorId);
            doneIds.add(roleId);
            ObjectNode it = objectMapper.createObjectNode();
            it.put("changeType", "REMOVE");
            ObjectNode roleNode = it.putObject("role");
            roleNode.put("roleTypeCode", typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()));
            roleNode.put("roleExternalId", role.getExternalId());
            roleNode.put("roleName", role.getName() != null ? role.getName() : "");
            itemsJson.add(it);
        }
        if (doneIds.isEmpty()) {
            return;
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
        Set<Long> uniqueRoleIds = new LinkedHashSet<>(doneIds);
        Long[] roleArr = uniqueRoleIds.toArray(Long[]::new);
        permissionChangeDomainService.record(
            new PermissionChangeDomainService.ChangeLogContext(
                tenantId, null, operatorId, null, "MANUAL", "abstract-role-batch-remove"),
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
            "soft-deleted " + doneIds.size() + " role(s), ids=" + doneIds,
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
            .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            .and(ABSTRACT_ROLE.STATUS.eq(1));
        if (bizDomainId != null) {
            qw.and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(bizDomainId).or(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()));
        } else {
            qw.and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull());
        }

        List<AbstractRole> allRoles = abstractRoleMapper.selectListByQuery(qw);

        // Build tree: root roles are those with parentId=null or parentId pointing to deleted roles
        List<AbstractRole> roots = allRoles.stream()
            .filter(r -> r.getParentId() == null)
            .collect(Collectors.toList());

        List<RoleTreeResp> result = new ArrayList<>();
        for (AbstractRole root : roots) {
            result.add(new RoleTreeResp(buildTreeNode(root, allRoles)));
        }
        return result;
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
            .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0));
        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return queryWrapper.and(ABSTRACT_ROLE.ID.eq(-1L));
            }
            queryWrapper.and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId).or(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()));
        }
        if (roleTypeCode != null && !roleTypeCode.isBlank()) {
            Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", roleTypeCode);
            if (roleType == null) {
                return queryWrapper.and(ABSTRACT_ROLE.ID.eq(-1L));
            }
            queryWrapper.and(ABSTRACT_ROLE.ROLE_TYPE.eq(roleType));
        }
        if (keyword != null && !keyword.isBlank()) {
            queryWrapper.and(
                ABSTRACT_ROLE.NAME.like("%" + keyword + "%")
                    .or(ABSTRACT_ROLE.EXTERNAL_ID.like("%" + keyword + "%"))
            );
        }
        return queryWrapper;
    }

    private RoleTreeNode buildTreeNode(AbstractRole role, List<AbstractRole> allRoles) {
        List<RoleTreeNode> children = allRoles.stream()
            .filter(r -> role.getId().equals(r.getParentId()))
            .map(r -> buildTreeNode(r, allRoles))
            .collect(Collectors.toList());

        return new RoleTreeNode(
            role.getId(), role.getTenantId(), role.getParentId(),
            typeResolutionService.resolveTypeCode(role.getTenantId(), "role_type", role.getRoleType()), role.getName(), role.getExternalId(),
            role.getStatus(), role.getSortOrder(), children
        );
    }

    private RoleResp toRoleResp(AbstractRole role) {
        String roleTypeName = "";
        try {
            roleTypeName = RoleType.fromValue(role.getRoleType() != null ? role.getRoleType() : 0).getLabel();
        } catch (IllegalArgumentException ignored) {}

        return new RoleResp(
            role.getId(), role.getTenantId(), role.getBizDomainId(),
            role.getParentId(), typeResolutionService.resolveTypeCode(role.getTenantId(), "role_type", role.getRoleType()), roleTypeName,
            role.getExternalId(), role.getName(), role.getStatus(),
            role.getSortOrder(), role.getExtra(),
            role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}
