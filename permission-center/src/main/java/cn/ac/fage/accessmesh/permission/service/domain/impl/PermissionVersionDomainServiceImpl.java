package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;
import cn.ac.fage.accessmesh.permission.mapper.PermissionVersionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import cn.ac.fage.accessmesh.permission.entity.table.PermissionVersionTableDef;

/**
 * 权限版本领域服务实现类
 * <p>
 * 实现权限版本号的查询、递增和缓存管理。
 * 采用双层缓存架构（L1 Caffeine + L2 Redis）存储版本号。
 * </p>
 */
@Service
public class PermissionVersionDomainServiceImpl implements PermissionVersionDomainService {

    private static final String VERSION_KEY_PREFIX = "perm:permission-version:role:";
    private static final long VERSION_CACHE_TTL_HOURS = 1;

    private final PermissionVersionMapper versionMapper;
    private final PermCacheDomainService permCacheDomainService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造函数注入依赖
     *
     * @param versionMapper         版本数据访问层
     * @param permCacheDomainService 权限缓存领域服务
     * @param redisTemplate         Redis操作模板
     */
    public PermissionVersionDomainServiceImpl(PermissionVersionMapper versionMapper,
                                               PermCacheDomainService permCacheDomainService,
                                               RedisTemplate<String, Object> redisTemplate) {
        this.versionMapper = versionMapper;
        this.permCacheDomainService = permCacheDomainService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取角色的当前权限版本号
     * <p>
     * 采用双层缓存策略：先查L1 Caffeine，再查L2 Redis，最后查数据库。
     * 数据库查询结果自动填充缓存。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 当前版本号，无记录时默认返回1
     */
    @Override
    public long getCurrentVersion(Long tenantId, Long roleId) {
        // L1缓存查询
        Optional<Long> cached = permCacheDomainService.getPermVersion(tenantId, roleId);
        if (cached.isPresent()) return cached.get();

        // L2 Redis缓存查询
        String l2Key = VERSION_KEY_PREFIX + tenantId + ":" + roleId;
        Object l2Val = redisTemplate.opsForValue().get(l2Key);
        if (l2Val instanceof Long) {
            long v = (Long) l2Val;
            permCacheDomainService.setPermVersion(tenantId, roleId, v);
            return v;
        }

        // 数据库查询最新版本记录
        PermissionVersion latest = versionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PermissionVersionTableDef.PERMISSION_VERSION.TENANT_ID.eq(tenantId))
                .and(PermissionVersionTableDef.PERMISSION_VERSION.ABSTRACT_ROLE_ID.eq(roleId))
                .orderBy(PermissionVersionTableDef.PERMISSION_VERSION.VERSION_NO.desc())
                .limit(1)
        );
        long version = latest != null ? latest.getVersionNo() : 1L;

        // 写入双层缓存
        permCacheDomainService.setPermVersion(tenantId, roleId, version);
        redisTemplate.opsForValue().set(l2Key, version, VERSION_CACHE_TTL_HOURS, TimeUnit.HOURS);
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
     * 事务操作确保数据一致性。
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

        // 更新缓存
        permCacheDomainService.setPermVersion(tenantId, roleId, newVersion);
        redisTemplate.opsForValue().set(VERSION_KEY_PREFIX + tenantId + ":" + roleId, newVersion, VERSION_CACHE_TTL_HOURS, TimeUnit.HOURS);
        return newVersion;
    }

    /**
     * 批量递增多个角色的权限版本号
     * <p>
     * 使用批量插入减少数据库网络往返，使用Pipeline批量写入Redis提升性能。
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
        List<PermissionVersion> allVersions = versionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PermissionVersionTableDef.PERMISSION_VERSION.TENANT_ID.eq(tenantId))
                .and(PermissionVersionTableDef.PERMISSION_VERSION.ABSTRACT_ROLE_ID.in(roleIds))
                .orderBy(PermissionVersionTableDef.PERMISSION_VERSION.VERSION_NO.desc())
        );

        // 构建roleId -> 最大版本号映射（利用排序，每个roleId第一次出现即为最大值）
        Map<Long, Long> roleIdToVersion = new HashMap<>();
        for (PermissionVersion pv : allVersions) {
            roleIdToVersion.putIfAbsent(pv.getAbstractRoleId(), pv.getVersionNo());
        }

        // 2. 批量创建新版本记录
        LocalDateTime now = LocalDateTime.now();
        List<PermissionVersion> newVersions = new ArrayList<>(roleIds.size());
        Map<String, Long> redisKeyToVersion = new HashMap<>(roleIds.size());

        for (Long roleId : roleIds) {
            Long currentVersion = roleIdToVersion.getOrDefault(roleId, 1L);
            Long newVersion = currentVersion + 1;

            PermissionVersion pv = new PermissionVersion();
            pv.setTenantId(tenantId);
            pv.setAbstractRoleId(roleId);
            pv.setVersionNo(newVersion);
            pv.setCreatedAt(now);
            newVersions.add(pv);

            // 准备Redis批量写入数据
            String l2Key = VERSION_KEY_PREFIX + tenantId + ":" + roleId;
            redisKeyToVersion.put(l2Key, newVersion);

            // 更新L1缓存
            permCacheDomainService.setPermVersion(tenantId, roleId, newVersion);
        }

        // 3. 批量插入数据库
        versionMapper.insertBatch(newVersions);

        // 4. 批量写入L2缓存（使用Pipeline提高性能）
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, Long> entry : redisKeyToVersion.entrySet()) {
                byte[] keyBytes = entry.getKey().getBytes();
                byte[] valueBytes = entry.getValue().toString().getBytes();
                connection.set(keyBytes, valueBytes);
                connection.expire(keyBytes, VERSION_CACHE_TTL_HOURS * 3600);
            }
            return null;
        });
    }
}