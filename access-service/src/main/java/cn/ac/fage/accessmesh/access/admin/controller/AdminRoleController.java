package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.access.application.query.UserRoleQueryService;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理控制器
 * <p>
 * 合并后（T-ACCESS-006）角色与授权管理由 permission 域直接提供，本控制器仅保留：
 * 读接口（/role/list、/role/my-info）经跨域只读查询服务聚合；写接口（/role/create、
 * /role/grant-menu、/role/revoke-menu）保留映射但恒拒绝（向后兼容，请改用
 * /api/perm/abstract-role、/api/perm/role-resource-permission）。
 * </p>
 */
@RestController
@RequestMapping("/role")
public class AdminRoleController {

    private static final Logger log = LoggerFactory.getLogger(AdminRoleController.class);

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
    public PermResult<ItemsResp<RoleListItemResp>> listRoles(@RequestBody(required = false) RoleListQueryReq req) {
        List<String> typeCodes = req != null && req.roleTypeCodes() != null
            ? req.roleTypeCodes()
            : null;
        return PermResult.success(new ItemsResp<>(userRoleQueryService.listRoles(typeCodes)));
    }

    /**
     * 创建组织关联角色【退役接口】
     * <p>
     * T-ACCESS-005 起组织/岗位角色（ORG/POSITION）由组织写入投影自动产生
     * （LocalProjectionDomainService.upsertAdminOrg），T-ACCESS-006 起 admin 侧角色
     * 管理整体移交 permission 域，本接口必然以 20045（LOCAL_PROJECTION_IMMUTABLE）
     * 拒绝——保留映射仅为向后兼容，调用方不得依赖其成功。
     * </p>
     *
     * @param req 角色创建请求，包含角色名称和组织ID
     * @return 创建成功的角色ID（实际恒抛 20045，不会返回）
     */
    @PostMapping("/create")
    public PermResult<Long> createRole(@Valid @RequestBody CreateRoleReq req) {
        throw new BizException(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode(),
            "组织/岗位角色只能由组织写入投影产生，角色管理已移交权限域 /api/perm/**");
    }

    /**
     * 为角色授予菜单权限【退役接口】
     * <p>
     * T-ACCESS-006 起角色授权由权限域提供（/api/perm/role-resource-permission/apply-grant-plan），
     * 本接口恒抛 10111（ROLE_API_RETIRED）拒绝——保留映射仅为向后兼容。
     * </p>
     *
     * @param req 角色菜单请求，包含角色ID和菜单ID
     * @return 操作成功结果（实际恒抛 10111，不会返回）
     */
    @PostMapping("/grant-menu")
    public PermResult<Void> grantMenu(@Valid @RequestBody RoleMenuReq req) {
        throw new BizException(AdminErrorCode.ROLE_API_RETIRED.getCode(),
            AdminErrorCode.ROLE_API_RETIRED.getMessage());
    }

    /**
     * 撤销角色的菜单权限【退役接口】
     * <p>
     * T-ACCESS-006 起角色授权由权限域提供，本接口恒抛 10111（ROLE_API_RETIRED）拒绝——
     * 保留映射仅为向后兼容。
     * </p>
     *
     * @param req 角色菜单请求，包含角色ID和菜单ID
     * @return 操作成功结果（实际恒抛 10111，不会返回）
     */
    @PostMapping("/revoke-menu")
    public PermResult<Void> revokeMenu(@Valid @RequestBody RoleMenuReq req) {
        throw new BizException(AdminErrorCode.ROLE_API_RETIRED.getCode(),
            AdminErrorCode.ROLE_API_RETIRED.getMessage());
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
    public PermResult<UserInfoResp> getMyInfo() {
        return PermResult.success(userMenuQueryService.loadUserRolesAndPermissions(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 角色创建请求记录
     *
     * @param roleName 角色名称
     * @param orgId    组织ID
     */
    public record CreateRoleReq(String roleName, Long orgId) {}

    /**
     * 角色菜单请求记录
     *
     * @param roleId 角色ID
     * @param menuId 菜单ID
     */
    public record RoleMenuReq(Long roleId, Long menuId) {}

    /**
     * 角色列表查询请求
     *
     * @param roleTypeCodes 角色类型编码列表（可选，为空则返回功能角色）
     */
    public record RoleListQueryReq(List<String> roleTypeCodes) {}
}
