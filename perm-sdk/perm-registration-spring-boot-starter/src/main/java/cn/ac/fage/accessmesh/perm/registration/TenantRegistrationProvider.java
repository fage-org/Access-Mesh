package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import java.util.List;
import java.util.Objects;

/** 一次提供各租户已绑定源侧代次的不可变发布；SDK 不生成代次或混合租户清单。 */
@FunctionalInterface
public interface TenantRegistrationProvider {
    List<TenantPublication> publications();
    record TenantPublication(RegistrationTarget target, PermissionManifestReq manifest) {
        public TenantPublication { Objects.requireNonNull(target); Objects.requireNonNull(manifest); }
    }
}
