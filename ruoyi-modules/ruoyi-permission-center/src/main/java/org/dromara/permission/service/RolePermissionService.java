package org.dromara.permission.service;

import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.vo.RolePermissionVo;

import java.util.List;

/**
 * 角色-资源-操作权限 role_resource_permission 服务
 */
public interface RolePermissionService {

    List<RolePermissionVo> list(RolePermissionListReq req);
}
