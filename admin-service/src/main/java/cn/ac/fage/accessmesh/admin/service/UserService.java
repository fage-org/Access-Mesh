package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface UserService {

    Long createUser(UserCreateReq req);

    BatchResultResp batchCreateUsers(UserBatchCreateReq req);

    void updateUser(UserUpdateReq req);

    void deleteUser(IdsReq req);

    void enableUser(IdsReq req);

    void disableUser(IdsReq req);

    UserResp getUser(Long id);

    PaginatedResult<UserPageItemResp> pageUsers(UserPageReq req);

    void resetPassword(Long userId, String newPassword);

    void batchResetPassword(IdsReq req, String newPassword);
}
