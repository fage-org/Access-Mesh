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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;

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

    /**
     * 构造函数注入依赖
     *
     * @param abstractRoleMapper 抽象角色数据访问层
     */
    public AbstractRoleDomainServiceImpl(AbstractRoleMapper abstractRoleMapper) {
        this.abstractRoleMapper = abstractRoleMapper;
    }

    /**
     * 创建角色
     * <p>
     * 在指定租户和域下创建新角色。
     * 角色类型通过roleType参数指定，支持组角色、组织角色、业务角色等。
     * 默认状态为启用（status=1），排序顺序默认为0。
     * </p>
     *
     * @param tenantId   租户ID
     * @param bizDomainId 业务域ID
     * @param parentId   父角色ID，可选
     * @param roleType   角色类型值（参考RoleType枚举）
     * @param externalId 外部标识，用于与外部系统关联
     * @param name       角色名称
     * @param sortOrder  排序顺序，可选
     * @param extra      扩展属性JSON，可选
     * @return 创建的角色ID
     */
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
        LocalDateTime now = LocalDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleteFlag(0L);
        abstractRoleMapper.insert(role);
        return role.getId();
    }

    /**
     * 删除角色及其子孙角色
     * <p>
     * 软删除指定角色。如果角色类型为组角色或组织角色，
     * 会级联删除其所有子孙角色。使用CTE递归查询一次性获取
     * 所有子孙角色ID，然后批量软删除，避免N+1问题。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long tenantId, Long roleId) {
        AbstractRole role = selectValidById(tenantId, roleId);
        if (role == null) return;

        LocalDateTime now = LocalDateTime.now();

        if (role.getRoleType() != null
            && (role.getRoleType() == RoleType.GROUP_ROLE.getValue()
                || role.getRoleType() == RoleType.ORG.getValue())) {
            // 使用CTE查询获取所有子孙ID，然后批量删除（性能优化）
            List<Long> descendantIds = abstractRoleMapper.selectDescendantIds(tenantId, roleId);
            if (!descendantIds.isEmpty()) {
                abstractRoleMapper.softDeleteBatch(tenantId, descendantIds, now);
            }
        }

        // 删除角色本身
        abstractRoleMapper.softDeleteBatch(tenantId, java.util.List.of(roleId), now);
    }

    /**
     * 查询子角色列表
     * <p>
     * 查询指定父角色下的直接子角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param parentId 父角色ID
     * @return 子角色列表
     */
    @Override
    public List<AbstractRole> listChildren(Long tenantId, Long parentId) {
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.PARENT_ID.eq(parentId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 解析子孙角色ID列表
     * <p>
     * 使用CTE递归查询一次性获取所有子孙角色ID，避免N+1问题。
     * 不包含角色本身。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 子孙角色ID列表，无子孙返回空列表
     */
    @Override
    public List<Long> resolveDescendantIds(Long tenantId, Long roleId) {
        // 使用 CTE 递归查询一次性获取所有子孙角色ID，避免 N+1 问题
        List<Long> descendantIds = abstractRoleMapper.selectDescendantIds(tenantId, roleId);
        return descendantIds != null ? descendantIds : new ArrayList<>();
    }

    /**
     * 软删除单个角色
     * <p>
     * 设置deleteFlag为角色ID实现软删除。
     * </p>
     *
     * @param roleId 角色ID
     * @param now    删除时间
     */
    private void softDeleteRole(Long roleId, LocalDateTime now) {
        AbstractRole role = new AbstractRole();
        role.setId(roleId);
        role.setDeleteFlag(roleId);
        role.setDeletedAt(now);
        abstractRoleMapper.update(role);
    }

    /**
     * 根据ID查询有效角色
     * <p>
     * 查询未删除的角色实体，包含租户校验。
     * 如果角色ID为null，直接返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色实体，不存在或已删除返回null
     */
    @Override
    public AbstractRole selectValidById(Long tenantId, Long roleId) {
        if (roleId == null) {
            return null;
        }
        return abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(roleId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 批量查询有效角色
     * <p>
     * 批量查询多个角色实体，包含租户和删除标志校验。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 角色列表，空集合返回空列表
     */
    @Override
    public List<AbstractRole> selectValidByIds(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(roleIds))
                .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 批量软删除角色
     * <p>
     * 批量设置角色的deleteFlag实现软删除。
     * 使用单条SQL批量更新，提高删除效率。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        abstractRoleMapper.softDeleteBatch(tenantId, new ArrayList<>(roleIds), now);
    }

    /**
     * 批量解析子孙角色ID列表
     * <p>
     * 批量使用CTE递归查询获取多个角色的所有子孙角色ID。
     * 用于批量删除时级联删除子孙角色。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 所有子孙角色ID列表（合并）
     */
    @Override
    public List<Long> resolveDescendantIdsBatch(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> descendantIds = abstractRoleMapper.selectDescendantIdsBatch(tenantId, roleIds);
        return descendantIds != null ? descendantIds : Collections.emptyList();
    }
}