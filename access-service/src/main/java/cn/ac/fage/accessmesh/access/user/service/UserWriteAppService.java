package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.access.infrastructure.dto.IdsReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.user.dto.resp.UserCreateResp;

/**
 * 用户跨域写编排：同一事务维护 sys_user 与本地权限投影。
 */
public interface UserWriteAppService {

    UserCreateResp createUser(UserCreateReq req);

    void updateUser(UserUpdateReq req);

    void deleteUser(IdsReq req);

    void updateStatus(UserUpdateStatusReq req);
}
