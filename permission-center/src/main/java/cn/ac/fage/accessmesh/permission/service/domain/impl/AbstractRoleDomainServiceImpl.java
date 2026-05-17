package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 抽象角色领域服务实现类
 * <p>
 * 提供抽象角色（AbstractRole）的CRUD操作和树形结构查询功能。
 * 抽象角色是权限系统的核心概念，用于组织用户并配置权限。
 * 支持多种角色类型：组角色（GROUP_ROLE）、组织角色（ORG）、业务角色等。
 * 组角色和组织角色删除时会级联删除其所有子孙角色。
 * 使用CTE递归查询高效获取子孙角色ID，避免N+1问题。
 * 所有写操作均使用事务保证数据一致性。
 * </p>
 */
@Service
public class AbstractRoleDomainServiceImpl implements AbstractRoleDomainService {

    private final AbstractRoleMapper abstractRoleMapper;

    public AbstractRoleDomainServiceImpl(AbstractRoleMapper abstractRoleMapper) {
        this.abstractRoleMapper = abstractRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(Long tenantId, Long parentId, Integer roleType,
                           String externalId, String name, Integer sortOrder, String extra) {
        RoleType rt = RoleType.fromValue(roleType);

        if (parentId != null) {
            AbstractRole parent = selectValidById(tenantId, parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Parent role not found: " + parentId);
            }
            if (!parent.getRoleType().equals(roleType)) {
                throw new IllegalArgumentException("Child roleType must match parent roleType: expected " + parent.getRoleType() + ", got " + roleType);
            }
        }

        AbstractRole role = new AbstractRole();
        role.setTenantId(tenantId);
        role.setParentId(parentId);
        role.setRoleType(roleType);
        role.setExternalId(externalId);
        role.setName(name);
        role.setStatus(1);
        role.setSortOrder(sortOrder != null ? sortOrder : 0);
        role.setExtra(extra);
        LocalDateTime now = LocalDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleteFlag(0L);
        abstractRoleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long tenantId, Long roleId) {
        AbstractRole role = selectValidById(tenantId, roleId);
        if (role == null) return;

        LocalDateTime now = LocalDateTime.now();

        if (role.getRoleType() != null
            && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                || role.getRoleType() == RoleType.ORG.getValue())) {
            List<Long> descendantIds = abstractRoleMapper.selectDescendantIds(tenantId, roleId);
            if (!descendantIds.isEmpty()) {
                abstractRoleMapper.softDeleteBatch(tenantId, descendantIds, now);
            }
        }

        abstractRoleMapper.softDeleteBatch(tenantId, java.util.List.of(roleId), now);
    }

    @Override
    public List<AbstractRole> listChildren(Long tenantId, Long parentId) {
        return abstractRoleMapper.selectChildren(tenantId, parentId);
    }

    @Override
    public AbstractRole selectValidById(Long tenantId, Long roleId) {
        if (roleId == null) {
            return null;
        }
        return abstractRoleMapper.selectValidById(tenantId, roleId);
    }

    @Override
    public List<AbstractRole> selectValidByIds(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return abstractRoleMapper.selectValidByIds(tenantId, roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        abstractRoleMapper.softDeleteBatch(tenantId, new ArrayList<>(roleIds), now);
    }

    @Override
    public List<Long> resolveDescendantIdsBatch(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> descendantIds = abstractRoleMapper.selectDescendantIdsBatch(tenantId, roleIds);
        return descendantIds != null ? descendantIds : Collections.emptyList();
    }
}