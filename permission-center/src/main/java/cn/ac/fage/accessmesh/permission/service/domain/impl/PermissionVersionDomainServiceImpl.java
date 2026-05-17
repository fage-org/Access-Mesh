package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;
import cn.ac.fage.accessmesh.permission.mapper.PermissionVersionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 权限版本领域服务实现类
 * <p>
 * 实现权限版本号的查询、递增和缓存管理。
 * 通过 PermCacheDomainService 委托统一 CacheService 管理 L1/L2 缓存。
 * </p>
 */
@Service
public class PermissionVersionDomainServiceImpl implements PermissionVersionDomainService {

    private final PermissionVersionMapper versionMapper;
    private final PermCacheDomainService permCacheDomainService;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param versionMapper         版本数据访问层
     * @param permCacheDomainService 权限缓存领域服务（委托 CacheService）
     * @param cacheService          统一缓存服务
     */
    public PermissionVersionDomainServiceImpl(PermissionVersionMapper versionMapper,
                                               PermCacheDomainService permCacheDomainService,
                                               CacheService cacheService) {
        this.versionMapper = versionMapper;
        this.permCacheDomainService = permCacheDomainService;
        this.cacheService = cacheService;
    }

    /**
     * 获取角色的当前权限版本号
     * <p>
     * 通过 PermCacheDomainService 委托 CacheService 管理 L1/L2 缓存。
     * 数据库查询结果自动填充缓存。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 当前版本号，无记录时默认返回1
     */
    @Override
    public long getCurrentVersion(Long tenantId, Long roleId) {
        // 通过 PermCacheDomainService 查缓存（已处理 L1 + L2）
        Optional<Long> cached = permCacheDomainService.getPermVersion(tenantId, roleId);
        if (cached.isPresent()) return cached.get();

        // miss 后查数据库
        PermissionVersion latest = versionMapper.selectLatestByRole(tenantId, roleId);
        long version = latest != null ? latest.getVersionNo() : 1L;

        // 回填缓存
        permCacheDomainService.setPermVersion(tenantId, roleId, version);
        return version;
    }

    /**
     * 计算多个角色的最大版本号
     * <p>
     * 用于判断用户权限缓存是否需要更新，取所有角色的最新版本号
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 最大版本号
     */
    @Override
    public long calculateMaxVersion(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0L;
        }
        return roleIds.stream()
            .mapToLong(roleId -> getCurrentVersion(tenantId, roleId))
            .max()
            .orElse(0L);
    }

    /**
     * 构建权限版本键
     * <p>
     * 格式为 userId:maxVersion，用于生成缓存键。
     * 版本号变化时缓存键变化，触发缓存重建。
     * </p>
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 版本键字符串
     */
    @Override
    public String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds) {
        long maxVersion = calculateMaxVersion(tenantId, roleIds);
        return userId + ":" + maxVersion;
    }

    /**
     * 递增角色的权限版本号
     * <p>
     * 当角色权限变更时调用，版本号加1并写入数据库和缓存。
     * 事务操作确保数据一致性，缓存写入在事务提交后执行（避免回滚污染）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 新版本号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long increment(Long tenantId, Long roleId) {
        long current = getCurrentVersion(tenantId, roleId);
        long newVersion = current + 1;

        // 创建新版本记录
        PermissionVersion pv = new PermissionVersion();
        pv.setTenantId(tenantId);
        pv.setAbstractRoleId(roleId);
        pv.setVersionNo(newVersion);
        pv.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(pv);

        // 缓存写入延迟到事务提交后，避免回滚污染缓存
        // 正确做法：注册 afterCommit 钩子写入新版本，而不是先 evict 再 write
        final Long finalTenantId = tenantId;
        final Long finalRoleId = roleId;
        final long finalNewVersion = newVersion;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permCacheDomainService.setPermVersion(finalTenantId, finalRoleId, finalNewVersion);
                }
            });
        } else {
            // 无事务时直接写入
            permCacheDomainService.setPermVersion(tenantId, roleId, newVersion);
        }

        return newVersion;
    }

    /**
     * 批量递增多个角色的权限版本号
     * <p>
     * 使用批量插入减少数据库网络往返。
     * 缓存写入在事务提交后执行，避免回滚污染。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchIncrement(Long tenantId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        // 1. 批量查询所有roleId的版本记录（按版本号降序，便于取最大值）
        List<PermissionVersion> allVersions = versionMapper.selectAllByRolesOrdered(tenantId, roleIds);

        // 构建roleId -> 最大版本号映射（利用排序，每个roleId第一次出现即为最大值）
        Map<Long, Long> roleIdToVersion = new HashMap<>();
        for (PermissionVersion pv : allVersions) {
            roleIdToVersion.putIfAbsent(pv.getAbstractRoleId(), pv.getVersionNo());
        }

        // 2. 批量创建新版本记录
        LocalDateTime now = LocalDateTime.now();
        List<PermissionVersion> newVersions = new ArrayList<>(roleIds.size());
        Map<Long, Long> roleIdToNewVersion = new HashMap<>();

        for (Long roleId : roleIds) {
            Long currentVersion = roleIdToVersion.getOrDefault(roleId, 1L);
            Long newVersion = currentVersion + 1;

            PermissionVersion pv = new PermissionVersion();
            pv.setTenantId(tenantId);
            pv.setAbstractRoleId(roleId);
            pv.setVersionNo(newVersion);
            pv.setCreatedAt(now);
            newVersions.add(pv);

            roleIdToNewVersion.put(roleId, newVersion);
        }

        // 3. 批量插入数据库
        versionMapper.insertBatch(newVersions);

        // 4. 缓存写入延迟到事务提交后，避免回滚污染缓存
        final Long finalTenantId = tenantId;
        final Map<Long, Long> finalRoleIdToNewVersion = roleIdToNewVersion;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Map.Entry<Long, Long> entry : finalRoleIdToNewVersion.entrySet()) {
                        permCacheDomainService.setPermVersion(finalTenantId, entry.getKey(), entry.getValue());
                    }
                }
            });
        } else {
            // 无事务时直接写入
            for (Map.Entry<Long, Long> entry : roleIdToNewVersion.entrySet()) {
                permCacheDomainService.setPermVersion(tenantId, entry.getKey(), entry.getValue());
            }
        }
    }
}