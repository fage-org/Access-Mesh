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
import java.util.Map;

@Service
public class UserSyncHandlerImpl implements UserSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncHandlerImpl.class);
    private static final String SUBJECT_TYPE_ADMIN_USER = "ADMIN_USER";

    private final PermissionFeignClient permissionFeignClient;

    public UserSyncHandlerImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

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

        PermResult<Map<String, Object>> result = permissionFeignClient.syncUser(req);
        if (result == null || result.code() != 200 || result.data() == null) {
            log.warn("Failed to sync user to permission-center: userId={}, username={}",
                user.getId(), user.getUsername());
            return null;
        }

        Object idObj = result.data().get("id");
        if (idObj != null) {
            Long permUserId = Long.valueOf(idObj.toString());
            log.info("Synced user to permission-center: userId={}, permUserId={}",
                user.getId(), permUserId);
            return permUserId;
        }
        return null;
    }

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

    @Override
    public boolean deleteUserFromPermissionCenter(Long tenantId, Long permUserId) {
        if (permUserId == null) {
            return true; // 未同步过，视为成功
        }

        IdsReq req = new IdsReq(List.of(permUserId));
        PermResult<Void> result = permissionFeignClient.deleteUsers(req);
        if (result == null || result.code() != 200) {
            log.warn("Failed to delete user from permission-center: permUserId={}", permUserId);
            return false;
        }

        log.info("Deleted user from permission-center: permUserId={}", permUserId);
        return true;
    }

    @Override
    public String generateSyncVersion(SysUser user) {
        // 使用 updatedAt 时间戳作为版本号
        LocalDateTime updatedAt = user.getUpdatedAt();
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        return updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String buildUserExtra(SysUser user) {
        // 将额外信息打包为 JSON 字符串
        return String.format("{\"username\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\"}",
            user.getUsername(),
            user.getEmail() != null ? user.getEmail() : "",
            user.getPhone() != null ? user.getPhone() : ""
        );
    }
}