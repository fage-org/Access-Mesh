package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;
import cn.ac.fage.accessmesh.permission.mapper.PermissionVersionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限版本领域服务实现类
 * <p>
 * 实现权限版本号的查询、递增和缓存管理。
 * 直接使用 CacheService 管理 L1/L2 缓存。
 * </p>
 */
@Service
public class PermissionVersionDomainServiceImpl implements PermissionVersionDomainService {

    private final PermissionVersionMapper versionMapper;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param versionMapper 版本数据访问层
     * @param cacheService  统一缓存服务
     */
    public PermissionVersionDomainServiceImpl(PermissionVersionMapper versionMapper,
                                               CacheService cacheService) {
        this.versionMapper = versionMapper;
        this.cacheService = cacheService;
    }

    /**
     * 获取角色的当前权限版本号（内部方法）
     * <p>
     * 直接调用 CacheService 管理 L1/L2 缓存。
     * 数据库查询结果自动填充缓存。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 当前版本号，无记录时默认返回1
     */
    private long getCurrentVersion(Long tenantId, Long roleId) {
        // 直接调用 CacheService 查缓存（已处理 L1 + L2）
        Long cached = cacheService.get(PermCacheCatalog.PERMISSION_VERSION, tenantId, roleId);
        if (cached != null) return cached;

        // miss 后查数据库
        PermissionVersion latest = versionMapper.selectLatestByRole(tenantId, roleId);
        long version = latest != null ? latest.getVersionNo() : 1L;

        // 回填缓存
        cacheService.put(PermCacheCatalog.PERMISSION_VERSION, tenantId, roleId, version);
        return version;
    }

    /**
     * 批量获取多个角色的当前权限版本号
     * <p>
     * 优先走统一缓存批量读取，未命中再批量查询数据库并回填缓存。
     * 无版本记录的角色默认返回1，并写入缓存，避免后续重复回源。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 角色ID到当前版本号的映射
     */
    @Override
    public Map<Long, Long> batchGetCurrentVersions(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> normalizedRoleIds = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            if (roleId != null) {
                normalizedRoleIds.add(roleId);
            }
        }

        if (normalizedRoleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> result = new HashMap<>(
            cacheService.getBatch(PermCacheCatalog.PERMISSION_VERSION, tenantId, normalizedRoleIds));
        Set<Long> uncachedRoleIds = new LinkedHashSet<>(normalizedRoleIds);
        uncachedRoleIds.removeAll(result.keySet());
        if (uncachedRoleIds.isEmpty()) {
            return result;
        }

        List<PermissionVersion> versions = versionMapper.selectAllByRolesOrdered(tenantId, uncachedRoleIds);
        Map<Long, Long> latestVersionMap = new HashMap<>();
        for (PermissionVersion version : versions) {
            latestVersionMap.putIfAbsent(version.getAbstractRoleId(), version.getVersionNo());
        }

        Map<Long, Long> toCache = new HashMap<>();
        for (Long roleId : uncachedRoleIds) {
            Long currentVersion = latestVersionMap.getOrDefault(roleId, 1L);
            result.put(roleId, currentVersion);
            toCache.put(roleId, currentVersion);
        }

        cacheService.putBatch(PermCacheCatalog.PERMISSION_VERSION, tenantId, toCache);
        return result;
    }

    /**
     * 计算多个角色的最大版本号（内部方法）
     * <p>
     * 用于判断用户权限缓存是否需要更新，取所有角色的最新版本号
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 最大版本号
     */
    private long calculateMaxVersion(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0L;
        }
        return batchGetCurrentVersions(tenantId, roleIds).values().stream()
            .mapToLong(Long::longValue)
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
                    cacheService.put(PermCacheCatalog.PERMISSION_VERSION, finalTenantId, finalRoleId, finalNewVersion);
                }
            });
        } else {
            // 无事务时直接写入
            cacheService.put(PermCacheCatalog.PERMISSION_VERSION, tenantId, roleId, newVersion);
        }

        return newVersion;
    }
}