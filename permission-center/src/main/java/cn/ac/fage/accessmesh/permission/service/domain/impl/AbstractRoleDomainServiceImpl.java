package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.enums.RoleType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;

@Service
public class AbstractRoleDomainServiceImpl implements AbstractRoleDomainService {

    private final AbstractRoleMapper abstractRoleMapper;

    public AbstractRoleDomainServiceImpl(AbstractRoleMapper abstractRoleMapper) {
        this.abstractRoleMapper = abstractRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(Long tenantId, Long bizDomainId, Long parentId, Integer roleType,
                           String externalId, String name, Integer sortOrder, String extra) {
        RoleType rt = RoleType.fromValue(roleType);

        AbstractRole role = new AbstractRole();
        role.setTenantId(tenantId);
        role.setBizDomainId(bizDomainId);
        role.setParentId(parentId);
        role.setRoleType(roleType);
        role.setExternalId(externalId);
        role.setName(name);
        role.setStatus(1);
        role.setSortOrder(sortOrder != null ? sortOrder : 0);
        role.setExtra(extra);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setDeleteFlag(0L);
        abstractRoleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long tenantId, Long roleId) {
        AbstractRole role = abstractRoleMapper.selectOneById(roleId);
        if (role == null || role.getDeleteFlag() != 0L) return;

        LocalDateTime now = LocalDateTime.now();

        if (role.getRoleType() != null
            && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                || role.getRoleType() == RoleType.ORG.getValue())) {
            // Recursive delete children
            List<AbstractRole> children = abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ABSTRACT_ROLE.PARENT_ID.eq(roleId))
                    .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            );
            for (AbstractRole child : children) {
                softDeleteRole(child.getId(), now);
            }
        }

        softDeleteRole(roleId, now);
    }

    @Override
    public List<AbstractRole> listChildren(Long tenantId, Long parentId) {
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.PARENT_ID.eq(parentId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<Long> resolveDescendantIds(Long tenantId, Long roleId) {
        // 使用 CTE 递归查询一次性获取所有子孙角色ID，避免 N+1 问题
        List<Long> descendantIds = abstractRoleMapper.selectDescendantIds(tenantId, roleId);
        return descendantIds != null ? descendantIds : new ArrayList<>();
    }

    private void softDeleteRole(Long roleId, LocalDateTime now) {
        AbstractRole role = new AbstractRole();
        role.setId(roleId);
        role.setDeleteFlag(roleId);
        role.setDeletedAt(now);
        abstractRoleMapper.update(role);
    }

    @Override
    public AbstractRole selectValidById(Long tenantId, Long roleId) {
        if (roleId == null) {
            return null;
        }
        return abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(roleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
    }
}
