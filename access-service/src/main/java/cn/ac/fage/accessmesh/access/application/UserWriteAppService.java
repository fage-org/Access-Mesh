package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;

/**
 * 用户跨域写编排：同一事务维护 sys_user 与本地权限投影。
 */
public interface UserWriteAppService {

    UserCreateResp createUser(UserCreateReq req);

    void updateUser(UserUpdateReq req);

    void deleteUser(IdsReq req);

    void updateStatus(UserUpdateStatusReq req);

    /**
     * 内部锁定入口（登录失败达阈值触发）：sys_user.status=2 + 同步禁用权限投影。
     * 无权限门禁——登录路径无操作者（匿名上下文）也可调用；
     * 与 {@link #updateStatus} 不同，不校验操作者身份与 ENABLE 权限。
     */
    void lockUser(Long tenantId, Long userId);
}
