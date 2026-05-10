package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;

/**
 * 角色权限领域服务实现类
 * <p>
 * 管理角色的权限配置，包括查询、授予、撤销等操作。
 * 权限变更后自动递增版本号，确保缓存一致性。
 * </p>
 */
@Service
public class RolePermissionDomainServiceImpl implements RolePermissionDomainService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionDomainServiceImpl.class);

    private final RoleResourcePermissionMapper rolePermMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;

    /**
     * 构造函数注入依赖
     *
     * @param rolePermMapper              角色权限数据访问层
     * @param permissionVersionDomainService 权限版本领域服务
     */
    public RolePermissionDomainServiceImpl(RoleResourcePermissionMapper rolePermMapper,
                                            PermissionVersionDomainService permissionVersionDomainService) {
        this.rolePermMapper = rolePermMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
    }

    /**
     * 获取角色的权限快照
     * <p>
     * 查询角色的所有权限配置，包含版本号，用于权限校验
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色权限快照
     */
    @Override
    public RolePermSnapshot getRolePermissions(Long tenantId, Long roleId) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        long version = permissionVersionDomainService.getCurrentVersion(tenantId, roleId);

        List<RolePermSnapshot.RolePermEntry> entries = perms.stream()
            .map(p -> new RolePermSnapshot.RolePermEntry(
                p.getId(), p.getAbstractRoleId(),
                p.getResourceEntityId(), null, p.getResourceType(),
                p.getOperationPermissionId(), null, null,
                p.getGrantSource(),
                p.getCanGrant(), p.getConditionId(), p.getConditionId() != null,
                p.getDependOn()
            ))
            .collect(Collectors.toList());

        return new RolePermSnapshot(tenantId, roleId, version, entries);
    }

    /**
     * 批量授予角色权限
     * <p>
     * 批量插入权限记录，事务提交后递增版本号。
     * 版本递增使用TransactionSynchronization确保在事务提交后执行。
     * </p>
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param entries      待授予的权限条目列表
     * @param changeSource 变更来源
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(Long tenantId, Long roleId, List<RolePermSnapshot.RolePermEntry> entries, String changeSource) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<RoleResourcePermission> toInsert = new ArrayList<>(entries.size());
        for (RolePermSnapshot.RolePermEntry entry : entries) {
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(roleId);
            rp.setResourceEntityId(entry.resourceEntityId());
            rp.setOperationPermissionId(entry.operationPermissionId());
            rp.setResourceType(entry.resourceType());
            rp.setDependOn(entry.dependOn());
            rp.setScopeAll(false);
            rp.setCanGrant(entry.canGrant() != null && entry.canGrant());
            rp.setConditionId(entry.conditionId());
            rp.setGrantSource(changeSource != null ? changeSource : PermConstants.MaintainSource.MANUAL);
            LocalDateTime now = LocalDateTime.now();
            rp.setCreatedAt(now);
            rp.setUpdatedAt(now);
            rp.setDeleteFlag(0L);
            toInsert.add(rp);
        }
        rolePermMapper.insertBatch(toInsert);

        // 版本递增（事务提交后执行，避免缓存被回滚数据污染）
        final Long roleIdForCache = roleId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleIdForCache);
                }
            });
        }
    }

    /**
     * 批量撤销角色权限
     * <p>
     * 先验证权限归属，然后批量软删除。
     * 同时级联删除依赖该权限的子权限。
     * </p>
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID
     * @param permissionIds 待撤销的权限ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // 验证权限归属该角色
        long validCount = rolePermMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ID.in(permissionIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        if (validCount == 0) {
            return;
        }

        // 批量软删除权限（单条SQL，避免N+1）
        rolePermMapper.softDeleteBatch(tenantId, permissionIds, now);

        // 批量级联删除子权限（单条SQL，避免N+1）
        rolePermMapper.cascadeSoftDeleteChildren(tenantId, permissionIds, now);

        // 版本递增（事务提交后执行）
        final Long roleIdForCache = roleId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleIdForCache);
                }
            });
        }
    }

    /**
     * 撤销单个权限并级联删除子权限
     * <p>
     * 软删除指定权限，同时删除所有依赖该权限的子权限
     * </p>
     *
     * @param tenantId   租户ID
     * @param roleId     角色ID
     * @param permissionId 权限ID
     * @param deletedAt  删除时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermissionWithCascade(Long tenantId, Long roleId, Long permissionId, LocalDateTime deletedAt) {
        RoleResourcePermission rp = rolePermMapper.selectOneById(permissionId);
        if (rp != null && rp.getDeleteFlag() == 0L && rp.getAbstractRoleId().equals(roleId)
            && rp.getTenantId().equals(tenantId)) {
            // 软删除权限
            rp.setDeleteFlag(rp.getId());
            rp.setDeletedAt(deletedAt);
            rolePermMapper.update(rp);

            // 级联软删除子权限（批量SQL，避免N+1）
            rolePermMapper.cascadeSoftDeleteChildren(tenantId, List.of(permissionId), deletedAt);
        }
    }

    /**
     * 根据ID查询有效的权限记录
     *
     * @param tenantId     租户ID
     * @param roleId       角色ID（可选过滤条件）
     * @param permissionId 权限ID
     * @return 权限实体，不存在时返回null
     */
    @Override
    public RoleResourcePermission selectValidById(Long tenantId, Long roleId, Long permissionId) {
        if (permissionId == null) {
            return null;
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ID.eq(permissionId))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        if (roleId != null) {
            qw.and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId));
        }
        return rolePermMapper.selectOneByQuery(qw);
    }
}