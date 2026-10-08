package cn.ac.fage.accessmesh.access.auth.dto;

import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import java.time.LocalDateTime;

public record PlatformAccountResp(Long id, String username, String name, Integer status,
                                  boolean forceResetPwd, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static PlatformAccountResp from(PlatformAccount account) {
        return new PlatformAccountResp(account.getId(), account.getUsername(), account.getName(), account.getStatus(),
            Boolean.TRUE.equals(account.getForceResetPwd()), account.getCreatedAt(), account.getUpdatedAt());
    }
}
