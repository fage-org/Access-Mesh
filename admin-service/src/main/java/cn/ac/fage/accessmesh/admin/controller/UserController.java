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

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService;

    public UserController(UserService userService,
                          cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService) {
        this.userService = userService;
        this.roleProxyService = roleProxyService;
    }

    @PostMapping("/create")
    @AuditLog(module = "用户管理", action = "创建", targetType = "USER")
    public PermResult<Long> createUser(@Valid @RequestBody UserCreateReq req) {
        return PermResult.success(userService.createUser(req));
    }

    @PostMapping("/update")
    @AuditLog(module = "用户管理", action = "修改", targetType = "USER")
    public PermResult<Void> updateUser(@Valid @RequestBody UserUpdateReq req) {
        userService.updateUser(req);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "用户管理", action = "删除", targetType = "USER")
    public PermResult<Void> deleteUser(@Valid @RequestBody IdsReq req) {
        userService.deleteUser(req);
        return PermResult.success();
    }

    @PostMapping("/enable")
    @AuditLog(module = "用户管理", action = "启用", targetType = "USER")
    public PermResult<Void> enableUser(@Valid @RequestBody IdsReq req) {
        userService.enableUser(req);
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<UserResp> getUser(@Valid @RequestBody IdReq req) {
        return PermResult.success(userService.getUser(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<UserPageItemResp>> pageUsers(@Valid @RequestBody UserPageReq req) {
        return PermResult.success(userService.pageUsers(req));
    }

    @PostMapping("/reset-password")
    @AuditLog(module = "用户管理", action = "重置密码", targetType = "USER")
    public PermResult<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(req.userId(), req.newPassword());
        return PermResult.success();
    }

    @PostMapping("/user-menus")
    public PermResult<UserInfoResp> getUserMenus(@Valid @RequestBody IdReq req) {
        return PermResult.success(roleProxyService.loadUserRolesAndPermissions(req.id()));
    }
}
