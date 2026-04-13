package org.dromara.gateway.authz;

import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 权限快照客户端占位实现
 *
 * 当 HTTP 客户端未启用时使用此实现，返回空快照
 *
 * @author RuoYi-Cloud-Plus
 */
@Component
@ConditionalOnProperty(prefix = "gateway.authz", name = "http-client-enabled", havingValue = "false", matchIfMissing = true)
public class NoopPermissionSnapshotClient implements PermissionSnapshotClient {

    @Override
    public InterfacePermissionSnapshot loadSnapshot(PrincipalContext principalContext) {
        InterfacePermissionSnapshot snapshot = new InterfacePermissionSnapshot();
        snapshot.setTenantId(principalContext.getTenantId());
        snapshot.setSubjectKey(principalContext.getSubjectKey());
        snapshot.setPermissionVersion(principalContext.getPermissionVersion());
        snapshot.setGeneratedAtEpochMilli(System.currentTimeMillis());
        return snapshot;
    }
}
