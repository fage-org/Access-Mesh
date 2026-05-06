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

@Service
public class PermissionVersionDomainServiceImpl implements PermissionVersionDomainService {

    private static final String VERSION_KEY_PREFIX = "perm:permission-version:role:";
    private static final long VERSION_CACHE_TTL_HOURS = 1;

    private final PermissionVersionMapper versionMapper;
    private final PermCacheDomainService permCacheDomainService;
    private final RedisTemplate<String, Object> redisTemplate;

    public PermissionVersionDomainServiceImpl(PermissionVersionMapper versionMapper,
                                               PermCacheDomainService permCacheDomainService,
                                               RedisTemplate<String, Object> redisTemplate) {
        this.versionMapper = versionMapper;
        this.permCacheDomainService = permCacheDomainService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long getCurrentVersion(Long tenantId, Long roleId) {
        Optional<Long> cached = permCacheDomainService.getPermVersion(tenantId, roleId);
        if (cached.isPresent()) return cached.get();

        String l2Key = VERSION_KEY_PREFIX + tenantId + ":" + roleId;
        Object l2Val = redisTemplate.opsForValue().get(l2Key);
        if (l2Val instanceof Long) {
            long v = (Long) l2Val;
            permCacheDomainService.setPermVersion(tenantId, roleId, v);
            return v;
        }

        PermissionVersion latest = versionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PermissionVersionTableDef.PERMISSION_VERSION.TENANT_ID.eq(tenantId))
                .and(PermissionVersionTableDef.PERMISSION_VERSION.ABSTRACT_ROLE_ID.eq(roleId))
                .orderBy(PermissionVersionTableDef.PERMISSION_VERSION.VERSION_NO.desc())
                .limit(1)
        );
        long version = latest != null ? latest.getVersionNo() : 1L;
        // TODO: Redis 操作竞态条件风险
        // 问题：当前 set + expire 操作不具备原子性，可能导致缓存击穿或短暂不一致
        // 建议：使用 Pipeline SETEX 或 Lua 脚本保证原子性
        // 优先级：P2（性能优化，可关注但不强制整改）
        permCacheDomainService.setPermVersion(tenantId, roleId, version);
        redisTemplate.opsForValue().set(l2Key, version, VERSION_CACHE_TTL_HOURS, TimeUnit.HOURS);
        return version;
    }

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

    @Override
    public String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds) {
        long maxVersion = calculateMaxVersion(tenantId, roleIds);
        return userId + ":" + maxVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long increment(Long tenantId, Long roleId) {
        long current = getCurrentVersion(tenantId, roleId);
        long newVersion = current + 1;
        PermissionVersion pv = new PermissionVersion();
        pv.setTenantId(tenantId);
        pv.setAbstractRoleId(roleId);
        pv.setVersionNo(newVersion);
        pv.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(pv);
        permCacheDomainService.setPermVersion(tenantId, roleId, newVersion);
        redisTemplate.opsForValue().set(VERSION_KEY_PREFIX + tenantId + ":" + roleId, newVersion, VERSION_CACHE_TTL_HOURS, TimeUnit.HOURS);
        return newVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchIncrement(Long tenantId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        // 1. 批量查询所有 roleId 的版本记录（按版本号降序，便于取最大值）
        List<PermissionVersion> allVersions = versionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PermissionVersionTableDef.PERMISSION_VERSION.TENANT_ID.eq(tenantId))
                .and(PermissionVersionTableDef.PERMISSION_VERSION.ABSTRACT_ROLE_ID.in(roleIds))
                .orderBy(PermissionVersionTableDef.PERMISSION_VERSION.VERSION_NO.desc())
        );

        // 构建 roleId -> 最大版本号 Map（利用排序，每个 roleId 第一次出现即为最大值）
        Map<Long, Long> roleIdToVersion = new HashMap<>();
        for (PermissionVersion pv : allVersions) {
            roleIdToVersion.putIfAbsent(pv.getAbstractRoleId(), pv.getVersionNo());
        }

        // 2. 批量创建新版本记录
        // 注意：与 increment 保持一致，无版本记录时默认版本号为 1L
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

            // 准备 Redis 批量写入的数据
            String l2Key = VERSION_KEY_PREFIX + tenantId + ":" + roleId;
            redisKeyToVersion.put(l2Key, newVersion);

            // 更新 L1 缓存
            permCacheDomainService.setPermVersion(tenantId, roleId, newVersion);
        }

        // 3. 批量插入数据库
        versionMapper.insertBatch(newVersions);

        // 4. 批量写入 L2 缓存（使用 pipelined 提高性能，每个 key 单独设置 TTL）
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
