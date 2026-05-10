package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.ResetPasswordReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.admin.service.UserService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 * <p>
 * 提供用户的CRUD操作、分页查询、密码重置等功能。
 * 用户是系统的基础主体，用于登录认证和权限分配。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService;

    /**
     * 构造函数注入依赖
     *
     * @param userService       用户管理服务
     * @param roleProxyService  角色代理服务，用于查询用户角色和权限
     */
    public UserController(UserService userService,
                          cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService) {
        this.userService = userService;
        this.roleProxyService = roleProxyService;
    }

    /**
     * 创建用户
     * <p>
     * 创建新用户，设置用户名、密码、手机号等基本信息。
     * </p>
     *
     * @param req 用户创建请求，包含用户基本信息
     * @return 创建成功的用户ID
     */
    @PostMapping("/create")
    @AuditLog(module = "用户管理", action = "创建", targetType = "USER")
    public PermResult<Long> createUser(@Valid @RequestBody UserCreateReq req) {
        return PermResult.success(userService.createUser(req));
    }

    /**
     * 更新用户信息
     * <p>
     * 更新用户的姓名、手机号、邮箱、状态等属性。
     * </p>
     *
     * @param req 用户更新请求，包含用户ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    @AuditLog(module = "用户管理", action = "修改", targetType = "USER")
    public PermResult<Void> updateUser(@Valid @RequestBody UserUpdateReq req) {
        userService.updateUser(req);
        return PermResult.success();
    }

    /**
     * 删除用户
     * <p>
     * 批量删除用户，会同时处理用户与组织、角色的关联关系。
     * </p>
     *
     * @param req ID集合请求，包含待删除的用户ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    @AuditLog(module = "用户管理", action = "删除", targetType = "USER")
    public PermResult<Void> deleteUser(@Valid @RequestBody IdsReq req) {
        userService.deleteUser(req);
        return PermResult.success();
    }

    /**
     * 启用用户
     * <p>
     * 批量启用用户，使其可以正常登录系统。
     * </p>
     *
     * @param req ID集合请求，包含待启用的用户ID列表
     * @return 操作成功结果
     */
    @PostMapping("/enable")
    @AuditLog(module = "用户管理", action = "启用", targetType = "USER")
    public PermResult<Void> enableUser(@Valid @RequestBody IdsReq req) {
        userService.enableUser(req);
        return PermResult.success();
    }

    /**
     * 获取用户详情
     * <p>
     * 根据用户ID查询用户的完整信息。
     * </p>
     *
     * @param req ID请求，包含用户ID
     * @return 用户详情信息
     */
    @PostMapping("/detail")
    public PermResult<UserResp> getUser(@Valid @RequestBody IdReq req) {
        return PermResult.success(userService.getUser(req.id()));
    }

    /**
     * 分页查询用户列表
     * <p>
     * 支持按用户名、手机号、状态等条件过滤，返回分页结果。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页用户列表结果
     */
    @PostMapping("/page")
    public PermResult<PaginatedResult<UserPageItemResp>> pageUsers(@Valid @RequestBody UserPageReq req) {
        return PermResult.success(userService.pageUsers(req));
    }

    /**
     * 重置用户密码
     * <p>
     * 将用户密码重置为指定的新密码。
     * 用于管理员帮助用户重置密码。
     * </p>
     *
     * @param req 密码重置请求，包含用户ID和新密码
     * @return 操作成功结果
     */
    @PostMapping("/reset-password")
    @AuditLog(module = "用户管理", action = "重置密码", targetType = "USER")
    public PermResult<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(req.userId(), req.newPassword());
        return PermResult.success();
    }

    /**
     * 查询用户的菜单和权限
     * <p>
     * 查询指定用户有权访问的菜单列表和权限标识。
     * 用于前端动态生成导航菜单和按钮权限。
     * </p>
     *
     * @param req ID请求，包含用户ID
     * @return 用户信息响应，包含菜单列表和权限标识
     */
    @PostMapping("/user-menus")
    public PermResult<UserInfoResp> getUserMenus(@Valid @RequestBody IdReq req) {
        return PermResult.success(roleProxyService.loadUserRolesAndPermissions(req.id()));
    }
}