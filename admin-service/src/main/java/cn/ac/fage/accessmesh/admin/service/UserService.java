package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserQuery;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface UserService {

    Long createUser(UserCreateReq req);

    void updateUser(UserUpdateReq req);

    void deleteUser(IdsReq req);

    void enableUser(IdsReq req);

    UserResp getUser(Long id);

    PaginatedResult<UserPageItemResp> pageUsers(PageReq pageReq, UserQuery query);

    void resetPassword(Long userId, String newPassword);
}
