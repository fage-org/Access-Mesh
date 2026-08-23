package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MemberCandidatesReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.ResetPasswordReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserBatchCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.MemberCandidateItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.ResetPasswordResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.access.admin.service.UserService;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.dev33.satoken.stp.StpUtil;
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
public class AdminUserController {

    private final UserService userService;
    private final UserMenuQueryService userMenuQueryService;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param userService           用户管理服务
     * @param userMenuQueryService  跨域用户菜单聚合查询服务（/user/user-menus）
     */
    public AdminUserController(UserService userService,
                          UserMenuQueryService userMenuQueryService,
                          AdminPermissionValidator permissionValidator) {
        this.userService = userService;
        this.userMenuQueryService = userMenuQueryService;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 创建用户
     * <p>
     * 创建新用户，设置用户名、密码、手机号等基本信息。
     * 支持创建时一步完成组织分配（orgId）。
     * 系统自动生成随机初始密码，通过响应返回。
     * </p>
     *
     * @param req 用户创建请求，包含用户基本信息和可选的组织分配
     * @return 创建成功的用户ID和初始密码
     */
    @PostMapping("/create")
    public PermResult<UserCreateResp> createUser(@Valid @RequestBody UserCreateReq req) {
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
    public PermResult<Void> deleteUser(@Valid @RequestBody IdsReq req) {
        userService.deleteUser(req);
        return PermResult.success();
    }

    /**
     * 批量启用/禁用用户
     * <p>
     * 根据请求中的 status 字段批量启用或禁用用户账号。
     * status=1 启用，status=0 禁用。
     * </p>
     *
     * @param req 用户状态变更请求，包含用户ID列表和目标状态
     * @return 操作成功结果
     */
    @PostMapping("/enable")
    public PermResult<Void> updateStatus(@Valid @RequestBody UserUpdateStatusReq req) {
        userService.updateStatus(req);
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
     * 查询候选用户（添加组织/岗位成员时使用）
     * <p>
     * 候选范围 = 默认组织树中操作者可见 ∩ 排除目标组织已有成员。
     * 契约依据：admin-service-api-contract.md §4.1.2
     * </p>
     *
     * @param req 候选用户查询请求（含 targetOrgId）
     * @return 分页候选用户列表
     */
    @PostMapping("/member-candidates")
    public PermResult<PaginatedResult<MemberCandidateItemResp>> memberCandidates(
        @Valid @RequestBody MemberCandidatesReq req) {
        return PermResult.success(userService.memberCandidates(req));
    }

    /**
     * 重置用户密码
     * <p>
     * 将用户密码重置为指定的新密码，或由系统自动生成随机密码。
     * 用于管理员帮助用户重置密码。
     * 响应中返回生效的密码明文（仅本次返回）。
     * </p>
     *
     * @param req 密码重置请求，包含用户ID和可选的新密码
     * @return 重置密码响应，包含生效的密码
     */
    @PostMapping("/reset-password")
    public PermResult<ResetPasswordResp> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        return PermResult.success(userService.resetPassword(req.userId(), req.newPassword()));
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
        // 权限边界：改己豁免——当前登录用户可查自己的权限信息；
        // 查询其他用户需 USER:VIEW 实例级门禁，防普通用户枚举 ID 读取他人角色/权限/组织
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!java.util.Objects.equals(req.id(), currentUserId)) {
            permissionValidator.checkInstanceLevel(ResourceTypeCode.USER,
                String.valueOf(req.id()), AdminOperationCode.VIEW);
        }
        return PermResult.success(userMenuQueryService.loadUserRolesAndPermissions(req.id()));
    }
}