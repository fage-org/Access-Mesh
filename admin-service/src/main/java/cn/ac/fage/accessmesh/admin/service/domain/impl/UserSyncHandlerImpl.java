package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.service.domain.UserSyncHandler;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdsReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserSyncReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserResp;

/**
 * 用户同步处理器实现类
 * <p>
 * 将admin-service的用户同步到permission-center的abstract_user表。
 * 使用用户ID作为外部标识，支持幂等同步。
 * 设计约束：abstract_user 只表示访问主体；用户作为被管理对象时，
 * 还需要同步为 resource_entity(ADMIN_USER, code=sys_user.id)。
 * 当前类只覆盖主体同步，后续实现用户生命周期权限闭环时必须补齐
 * ADMIN_USER 管理资源同步。
 * </p>
 */
@Service
public class UserSyncHandlerImpl implements UserSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncHandlerImpl.class);
    private static final String SUBJECT_TYPE_ADMIN_USER = "ADMIN_USER";

    private final PermissionFeignClient permissionFeignClient;

    /**
     * 构造函数
     *
     * @param permissionFeignClient 权限中心Feign客户端
     */
    public UserSyncHandlerImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    /**
     * 同步用户到权限中心
     * <p>
     * 创建或更新abstract_user，返回permUserId。
     * 使用updatedAt时间戳作为版本号，确保幂等同步。
     * 本方法不得被视为完整的用户管理资源同步；ADMIN_USER实例级权限
     * 还依赖用户 resource_entity 的独立同步。
     * </p>
     *
     * @param tenantId 租户ID
     * @param user     用户实体
     * @return permission-center的abstract_user.id，失败返回null
     */
    @Override
    public Long syncUserToPermissionCenter(Long tenantId, SysUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        String version = generateSyncVersion(user);
        UserSyncReq req = new UserSyncReq(
            SUBJECT_TYPE_ADMIN_USER,
            String.valueOf(user.getId()), // externalId = admin-service 用户ID
            user.getName(),
            user.getStatus() != null && user.getStatus() == 1,
            buildUserExtra(user),
            version
        );

        PermResult<UserResp> result = permissionFeignClient.syncUser(req);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            log.warn("同步用户到权限中心失败: userId={}, username={}",
                user.getId(), user.getUsername());
            return null;
        }

        Long permUserId = result.getData().id();
        log.info("同步用户到权限中心成功: userId={}, permUserId={}",
            user.getId(), permUserId);
        return permUserId;
    }

    /**
     * 批量同步用户到权限中心
     *
     * @param tenantId 租户ID
     * @param users    用户列表
     * @return 同步成功数量
     */
    @Override
    public int batchSyncUsers(Long tenantId, Iterable<SysUser> users) {
        int successCount = 0;
        for (SysUser user : users) {
            Long permUserId = syncUserToPermissionCenter(tenantId, user);
            if (permUserId != null) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * 从权限中心删除用户
     * <p>
     * 通过Feign调用权限中心删除指定的抽象用户。
     * </p>
     *
     * @param tenantId   租户ID
     * @param permUserId 权限中心的用户ID
     * @return 是否成功
     */
    @Override
    public boolean deleteUserFromPermissionCenter(Long tenantId, Long permUserId) {
        if (permUserId == null) {
            return true; // 未同步过，视为成功
        }

        IdsReq req = new IdsReq(List.of(permUserId));
        PermResult<Void> result = permissionFeignClient.deleteUsers(req);
        if (result == null || result.getCode() != 200) {
            log.warn("从权限中心删除用户失败: permUserId={}", permUserId);
            return false;
        }

        log.info("从权限中心删除用户成功: permUserId={}", permUserId);
        return true;
    }

    /**
     * 生成同步版本号
     * <p>
     * 使用updatedAt时间戳作为版本号，用于幂等同步。
     * 每次变更生成新版本，确保数据一致性。
     * </p>
     *
     * @param user 用户实体
     * @return 版本号字符串
     */
    @Override
    public String generateSyncVersion(SysUser user) {
        LocalDateTime updatedAt = user.getUpdatedAt();
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        return updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 构建用户扩展信息JSON
     * <p>
     * 将用户的额外信息打包为JSON字符串格式。
     * </p>
     *
     * @param user 用户实体
     * @return JSON字符串
     */
    private String buildUserExtra(SysUser user) {
        return String.format("{\"username\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}",
            user.getUsername(),
            user.getEmail() != null ? user.getEmail() : "",
            user.getPhone() != null ? user.getPhone() : ""
        );
    }
}
