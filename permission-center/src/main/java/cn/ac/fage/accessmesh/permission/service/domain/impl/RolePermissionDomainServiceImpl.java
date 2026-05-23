package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
     * 批量撤销角色权限
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

        // 查询实际属于该角色的有效权限ID
        List<RoleResourcePermission> validPerms = rolePermMapper.selectValidByIds(tenantId, roleId, permissionIds);
        if (validPerms.isEmpty()) {
            return;
        }
        List<Long> validIds = validPerms.stream().map(RoleResourcePermission::getId).collect(Collectors.toList());

        // 批量软删除权限（仅删除属于该角色的有效权限）
        rolePermMapper.softDeleteBatch(tenantId, validIds, now);

        // 批量级联删除子权限（仅基于有效权限ID）
        rolePermMapper.cascadeSoftDeleteChildren(tenantId, validIds, now);

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
        return rolePermMapper.selectValidById(tenantId, roleId, permissionId);
    }
}