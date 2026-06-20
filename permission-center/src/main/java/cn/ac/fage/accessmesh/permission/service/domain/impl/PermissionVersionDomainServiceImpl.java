package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 权限版本领域服务实现类
 * <p>
 * T-PERM-003（2026-06-20）删除 permission_version 持久化层后，本实现不再依赖
 * {@code PermissionVersionMapper} / {@code CacheService}，仅保留占位令牌构造。
 * </p>
 *
 * @see PermissionVersionDomainService 占位说明
 */
@Service
public class PermissionVersionDomainServiceImpl implements PermissionVersionDomainService {

    /**
     * 构造函数
     */
    public PermissionVersionDomainServiceImpl() {
    }

    @Override
    public String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds) {
        // 占位实现：基于 userId + 有序 roleIds 的稳定指纹，不再读 permission_version 表。
        // 权限内容失效改由 Redis pub/sub 广播（T-PERM-006）+ TTL 兜底承担。
        return userId + ":" + fingerprint(roleIds);
    }

    /**
     * 计算角色集合的稳定指纹（有序拼接后取 hashCode）
     *
     * @param roleIds 角色ID集合
     * @return 指纹字符串
     */
    private String fingerprint(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return "empty";
        }
        List<Long> sorted = new ArrayList<>(roleIds);
        java.util.Collections.sort(sorted);
        return Integer.toHexString(sorted.hashCode());
    }
}
