package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;
import cn.ac.fage.accessmesh.permission.mapper.PermissionVersionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import static cn.ac.fage.accessmesh.permission.entity.table.PermissionVersionTableDef.PERMISSION_VERSION;

@Service
public class PermissionVersionDomainServiceImpl implements PermissionVersionDomainService {

    private static final String VERSION_KEY_PREFIX = "perm:permission-version:role:";

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
                .where(PERMISSION_VERSION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_VERSION.ABSTRACT_ROLE_ID.eq(roleId))
                .orderBy(PERMISSION_VERSION.VERSION_NO.desc())
                .limit(1)
        );
        long version = latest != null ? latest.getVersionNo() : 1L;
        permCacheDomainService.setPermVersion(tenantId, roleId, version);
        redisTemplate.opsForValue().set(l2Key, version);
        return version;
    }

    @Override
    @Transactional
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
        redisTemplate.opsForValue().set(VERSION_KEY_PREFIX + tenantId + ":" + roleId, newVersion);
        return newVersion;
    }

    @Override
    @Transactional
    public void batchIncrement(Long tenantId, Collection<Long> roleIds) {
        for (Long roleId : roleIds) {
            increment(tenantId, roleId);
        }
    }
}
