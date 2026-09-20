package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.Dependency;
import java.util.List;

/** 一次读取完整依赖；失败抛异常，不能返回伪造的空清单。无需返回资源目录。 */
@FunctionalInterface
public interface PermissionManifestProvider {
    List<Dependency> dependencies();
}
