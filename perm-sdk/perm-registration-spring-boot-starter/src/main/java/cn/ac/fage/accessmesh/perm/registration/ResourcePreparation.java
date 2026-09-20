package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import java.util.List;

/** 复用调用方同步程序；闭包应绑定明确资源输入及原代次，不能从依赖边猜完整资源目录。 */
@FunctionalInterface
public interface ResourcePreparation {
    List<SyncResultResp> prepare(RegistrationTarget target);
}
