package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysUser;

/**
 * 用户同步处理器
 * 将 admin-service 的用户同步到 permission-center 的 abstract_user
 */
public interface UserSyncHandler {

    /**
     * 同步用户到权限中心
     * 创建或更新 abstract_user，返回 permUserId
     *
     * @param tenantId 租户ID
     * @param user     用户实体
     * @return permission-center 的 abstract_user.id，失败返回 null
     */
    Long syncUserToPermissionCenter(Long tenantId, SysUser user);

    /**
     * 批量同步用户到权限中心
     *
     * @param tenantId 租户ID
     * @param users    用户列表
     * @return 同步成功数量
     */
    int batchSyncUsers(Long tenantId, Iterable<SysUser> users);

    /**
     * 从权限中心删除用户
     *
     * @param tenantId   租户ID
     * @param permUserId 权限中心的用户ID
     * @return 是否成功
     */
    boolean deleteUserFromPermissionCenter(Long tenantId, Long permUserId);

    /**
     * 生成同步版本号
     * 用于幂等同步，每次变更生成新版本
     *
     * @param user 用户实体
     * @return 版本号字符串
     */
    String generateSyncVersion(SysUser user);
}