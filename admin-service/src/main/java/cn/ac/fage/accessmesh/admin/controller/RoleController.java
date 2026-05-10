package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.enums.DefaultOpCode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理控制器
 * <p>
 * 提供角色管理的代理接口，转发请求到permission-center进行角色资源操作。
 * 角色用于组织用户并配置权限，用户通过角色获得菜单和操作权限。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/role")
public class RoleController {

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);

    private final RoleProxyService roleProxyService;

    /**
     * 构造函数注入依赖
     *
     * @param roleProxyService 角色代理服务，用于转发请求到permission-center
     */
    public RoleController(RoleProxyService roleProxyService) {
        this.roleProxyService = roleProxyService;
    }

    /**
     * 创建组织关联角色
     * <p>
     * 创建与组织关联的角色，用于组织级别的权限管理。
     * 角色创建后会自动关联到指定的组织。
     * </p>
     *
     * @param req 角色创建请求，包含角色名称和组织ID
     * @return 创建成功的角色ID
     */
    @PostMapping("/create")
    @AuditLog(module = "角色管理", action = "创建", targetType = "ROLE")
    public PermResult<Long> createRole(@Valid @RequestBody CreateRoleReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        return PermResult.success(roleProxyService.createRoleForOrg(req.roleName(), req.orgId(), tenantId));
    }

    /**
     * 为角色授予菜单权限
     * <p>
     * 将菜单的查看权限授予指定角色。
     * 角色获得菜单权限后，关联用户可以访问该菜单。
     * </p>
     *
     * @param req 角色菜单请求，包含角色ID和菜单ID
     * @return 操作成功结果
     */
    @PostMapping("/grant-menu")
    @AuditLog(module = "角色管理", action = "授权菜单", targetType = "ROLE")
    public PermResult<Void> grantMenu(@Valid @RequestBody RoleMenuReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        roleProxyService.grantMenuToRole(tenantId, req.roleId(), req.menuId(), DefaultOpCode.VIEW.getCode());
        return PermResult.success();
    }

    /**
     * 撤销角色的菜单权限
     * <p>
     * 从角色中移除菜单的访问权限。
     * 角色失去菜单权限后，关联用户将无法访问该菜单。
     * </p>
     *
     * @param req 角色菜单请求，包含角色ID和菜单ID
     * @return 操作成功结果
     */
    @PostMapping("/revoke-menu")
    @AuditLog(module = "角色管理", action = "撤销菜单", targetType = "ROLE")
    public PermResult<Void> revokeMenu(@Valid @RequestBody RoleMenuReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        roleProxyService.revokeMenuFromRole(tenantId, req.roleId(), req.menuId());
        return PermResult.success();
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
        return PermResult.success(roleProxyService.loadUserRolesAndPermissions(StpUtil.getLoginIdAsLong()));
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
}