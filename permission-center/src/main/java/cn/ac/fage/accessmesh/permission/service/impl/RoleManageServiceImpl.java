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
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;

@Service
public class RoleManageServiceImpl implements RoleManageService {

    private final AbstractRoleMapper abstractRoleMapper;
    private final AbstractRoleDomainService abstractRoleDomainService;
    private final PermCacheDomainService permCacheDomainService;

    public RoleManageServiceImpl(AbstractRoleMapper abstractRoleMapper,
                                 AbstractRoleDomainService abstractRoleDomainService,
                                 PermCacheDomainService permCacheDomainService) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.abstractRoleDomainService = abstractRoleDomainService;
        this.permCacheDomainService = permCacheDomainService;
    }

    @Override
    @Transactional
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        Long roleId = abstractRoleDomainService.createRole(
            tenantId, req.bizDomainId(), req.parentId(), req.roleType(),
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
    public RoleResp updateRole(Long tenantId, Long roleId, String name, Integer sortOrder, String extra, Long operatorId) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null || role.getDeleteFlag() != 0L || !role.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }

        if (name != null) role.setName(name);
        if (sortOrder != null) role.setSortOrder(sortOrder);
        if (extra != null) role.setExtra(extra);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        abstractRoleMapper.update(role);

        return toRoleResp(role);
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
    @Transactional
    public void setRoleStatus(Long tenantId, Long roleId, int status, Long operatorId) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null || role.getDeleteFlag() != 0L || !role.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }

        role.setStatus(status);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(operatorId);
        abstractRoleMapper.update(role);

        // If disabled, invalidate related caches
        if (status != 1) {
            permCacheDomainService.evictRolePermSnapshot(tenantId, roleId);
        }
    }

    @Override
    public List<RoleTreeResp> getRoleTree(Long tenantId, Long bizDomainId) {
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
    public List<RoleResp> listRoles(Long tenantId, int offset, int limit) {
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
                .limit(limit)
                .offset(offset)
        ).stream().map(this::toRoleResp).collect(Collectors.toList());
    }

    private RoleTreeNode buildTreeNode(AbstractRole role, List<AbstractRole> allRoles) {
        List<RoleTreeNode> children = allRoles.stream()
            .filter(r -> role.getId().equals(r.getParentId()))
            .map(r -> buildTreeNode(r, allRoles))
            .collect(Collectors.toList());

        return new RoleTreeNode(
            role.getId(), role.getTenantId(), role.getParentId(),
            role.getRoleType(), role.getName(), role.getExternalId(),
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
            role.getParentId(), role.getRoleType(), roleTypeName,
            role.getExternalId(), role.getName(), role.getStatus(),
            role.getSortOrder(), role.getExtra(),
            role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}
