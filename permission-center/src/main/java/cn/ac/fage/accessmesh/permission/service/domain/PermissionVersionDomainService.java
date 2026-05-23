package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Map;
import java.util.Set;

/**
 * 权限版本领域服务接口
 * <p>
 * 管理权限版本号，用于权限变更追踪和缓存失效判断。
 * 权限变更时版本号递增，可用于判断缓存是否需要更新。
 * </p>
 */
public interface PermissionVersionDomainService {

    /**
     * 批量获取多个角色的当前权限版本号
     * <p>
     * 优先走统一缓存批量读取，未命中时再批量回源数据库并回填缓存。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 角色ID到当前版本号的映射
     */
    Map<Long, Long> batchGetCurrentVersions(Long tenantId, Set<Long> roleIds);

    /**
     * 构建权限版本键
     * <p>
     * 用于生成缓存键，格式为 userId:maxVersion。
     * 当版本号变化时，缓存键变化，触发缓存重建。
     * </p>
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 版本键字符串，如"123:42"
     */
    String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds);

    /**
     * 递增角色的权限版本号
     * <p>
     * 当角色权限变更时调用，版本号加1并写入数据库和缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 新版本号
     */
    long increment(Long tenantId, Long roleId);
}