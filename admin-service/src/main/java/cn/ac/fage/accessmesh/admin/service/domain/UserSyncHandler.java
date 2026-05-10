package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysUser;

/**
 * 用户同步处理器接口
 * <p>
 * 将 admin-service 的用户数据同步到 permission-center 的 abstract_user 表。
 * 实现跨服务的用户数据同步机制，确保权限系统中的用户主体与后台管理用户保持一致。
 * 使用用户ID作为外部标识，支持幂等同步，每次变更生成新版本号。
 * </p>
 */
public interface UserSyncHandler {

    /**
     * 同步单个用户到权限中心
     * <p>
     * 在 permission-center 创建或更新对应的 abstract_user 记录。
     * 使用用户ID作为外部标识（externalId），确保幂等同步。
     * 同步版本号基于用户更新时间戳生成，避免重复同步。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param user     用户实体，包含用户的基本信息
     * @return permission-center 中的 abstract_user.id，同步失败返回 null
     */
    Long syncUserToPermissionCenter(Long tenantId, SysUser user);

    /**
     * 批量同步用户到权限中心
     * <p>
     * 遍历用户列表逐个同步到 permission-center。
     * 用于用户数据初始化或批量导入场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param users    用户列表，支持 Iterable 类型便于大数据量处理
     * @return 同步成功的用户数量
     */
    int batchSyncUsers(Long tenantId, Iterable<SysUser> users);

    /**
     * 从权限中心删除用户
     * <p>
     * 在 permission-center 软删除对应的 abstract_user 记录。
     * 用于用户删除后同步清理权限中心的用户主体数据。
     * </p>
     *
     * @param tenantId   租户ID，用于多租户隔离
     * @param permUserId 权限中心中的抽象用户ID
     * @return 删除成功返回 true，失败返回 false
     */
    boolean deleteUserFromPermissionCenter(Long tenantId, Long permUserId);

    /**
     * 生成同步版本号
     * <p>
     * 基于用户实体的更新时间戳生成版本号字符串。
     * 用于幂等同步，确保只有数据变更时才执行同步操作。
     * 版本号格式为 ISO_LOCAL_DATE_TIME。
     * </p>
     *
     * @param user 用户实体
     * @return 同步版本号字符串
     */
    String generateSyncVersion(SysUser user);
}