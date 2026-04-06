package org.dromara.permission.service;

import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.vo.UserRoleVo;

import java.util.List;

/**
 * 用户-角色 user_role 服务
 */
public interface UserRoleService {

    List<UserRoleVo> list(UserRoleListReq req);
}
