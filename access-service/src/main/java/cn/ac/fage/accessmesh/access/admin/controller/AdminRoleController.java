package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.access.application.query.UserRoleQueryService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器
 * <p>
 * 合并后（T-ACCESS-006）角色与授权管理由 permission 域直接提供（/api/perm/abstract-role、
 * /api/perm/role-resource-permission），本控制器仅保留读接口（/role/list、/role/my-info）
 * 经跨域只读查询服务聚合；原写代理接口（/role/create、/role/grant-menu、/role/revoke-menu）
 * 已删除（T-ADMIN-024，无映射 404）。
 * </p>
 */
@RestController
@RequestMapping("/role")
public class AdminRoleController {

    private final UserRoleQueryService userRoleQueryService;
    private final UserMenuQueryService userMenuQueryService;

    /**
     * 构造函数注入依赖
     *
     * @param userRoleQueryService 跨域角色组合查询服务（/role/list）
     * @param userMenuQueryService 跨域用户菜单聚合查询服务（/role/my-info）
     */
    public AdminRoleController(UserRoleQueryService userRoleQueryService,
                               UserMenuQueryService userMenuQueryService) {
        this.userRoleQueryService = userRoleQueryService;
        this.userMenuQueryService = userMenuQueryService;
    }

    /**
     * 查询功能角色列表
     * <p>
     * 查询指定类型的角色列表，默认仅返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * 排除 ORG 和 POSITION 类型。用于用户详情面板的角色候选列表。
     * </p>
     *
     * @param req 角色列表查询请求，可选 roleTypeCodes 过滤
     * @return 角色列表
     */
    @PostMapping("/list")
    public R<ItemsResp<RoleListItemResp>> listRoles(@RequestBody(required = false) RoleListQueryReq req) {
        List<String> typeCodes = req != null && req.roleTypeCodes() != null
            ? req.roleTypeCodes()
            : null;
        return R.ok(new ItemsResp<>(userRoleQueryService.listRoles(typeCodes)));
    }

    /**
     * 获取当前用户的角色和权限信息
     * <p>
     * 查询当前登录用户的角色列表和权限标识。
     * 用于前端展示用户角色和进行按钮权限控制。
     * </p>
     *
     * @return 用户信息响应，包含角色列表和权限标识
     */
    @PostMapping("/my-info")
    public R<UserInfoResp> getMyInfo() {
        return R.ok(userMenuQueryService.loadUserRolesAndPermissions(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 角色列表查询请求
     *
     * @param roleTypeCodes 角色类型编码列表（可选，为空则返回功能角色）
     */
    public record RoleListQueryReq(List<String> roleTypeCodes) {}
}
