package cn.ac.fage.accessmesh.access.role.controller;

import cn.ac.fage.accessmesh.access.auth.dto.UserInfoResp;
import cn.ac.fage.accessmesh.access.menu.service.UserMenuQueryAppService;
import cn.ac.fage.accessmesh.access.role.service.UserRoleQueryAppService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理控制器
 * <p>
 * 合并后（T-ACCESS-006）角色与授权管理由权限面直接提供（/api/access/abstract-role、
 * /api/access/role-resource-permission），本控制器仅保留读接口（/role/my-info）
 * 经跨域只读查询服务聚合；原写代理接口（/role/create、/role/grant-menu、/role/revoke-menu）
 * 已删除（T-ADMIN-024，无映射 404）；/role/list 已删除（T-FE-058，2026-09-23——功能角色
 * 候选迁 /abstract-role/list，管理轨旧通道无 keyword 无分页且仅类型级门禁，退役不留兼容层）。
 * </p>
 */
@RestController
@RequestMapping("/api/access/role")
public class AdminRoleController {

    private final UserRoleQueryAppService userRoleQueryService;
    private final UserMenuQueryAppService userMenuQueryService;

    /**
     * 构造函数注入依赖
     *
     * @param userRoleQueryService 跨域角色组合查询服务（/user-role/view）
     * @param userMenuQueryService 跨域用户菜单聚合查询服务（/role/my-info）
     */
    public AdminRoleController(UserRoleQueryAppService userRoleQueryService,
                               UserMenuQueryAppService userMenuQueryService) {
        this.userRoleQueryService = userRoleQueryService;
        this.userMenuQueryService = userMenuQueryService;
    }

    /**
     * 获取当前用户的角色和权限信息
     * <p>
     * 查询当前登录用户的角色列表和权限标识。
     * 用于前端展示用户角色和进行按钮权限控制。
     * </p>
     *
     * @return 用户信息响应，包含用户角色列表和权限标识
     */
    @PostMapping("/my-info")
    public R<UserInfoResp> getMyInfo() {
        return R.ok(userMenuQueryService.loadUserRolesAndPermissions(StpUtil.getLoginIdAsLong()));
    }
}
