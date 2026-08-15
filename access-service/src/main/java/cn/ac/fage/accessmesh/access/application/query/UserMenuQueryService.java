package cn.ac.fage.accessmesh.access.application.query;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;

/**
 * 用户菜单聚合查询服务（跨域只读，T-ACCESS-006）。
 * <p>
 * 聚合 admin 域（sys_user_org、sys_menu）与 permission 域（角色、有效权限码、
 * 菜单可见性判定）数据，供 /auth/user-menu、/user/user-menus、/role/my-info 使用。
 * 权限判定经 PermQueryEngine，不直查权限表做判定；所有读取走 query 包专用 Mapper，
 * 只读事务执行，不产生任何写 SQL。
 * </p>
 */
public interface UserMenuQueryService {

    /**
     * 加载用户的角色与有效权限（登录链路自查，无管理门禁）。
     *
     * @param userId 用户 ID
     * @return 用户信息响应（仅角色、权限、组织列表有值）
     */
    UserInfoResp loadUserRolesAndPermissions(Long userId);

    /**
     * 构建用户菜单树（含角色与权限码，/auth/user-menu 聚合）。
     *
     * @param userId 用户 ID
     * @return 菜单树响应（menus + roles + permissions）
     */
    UserMenuResp buildUserMenuTree(Long userId);
}
