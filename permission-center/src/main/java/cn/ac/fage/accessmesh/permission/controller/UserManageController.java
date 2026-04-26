package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/user")
public class UserManageController {

    private final UserManageService userManageService;

    public UserManageController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    /**
     * Sync user from external system (upsert).
     */
    @PostMapping("/sync")
    public PermResult<UserResp> syncUser(@Valid @RequestBody UserSyncReq req) {
        return PermResult.success(userManageService.syncUser(req));
    }

    /**
     * Get user by ID.
     */
    @PostMapping("/get")
    public PermResult<UserResp> getUser(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(userManageService.getUser(req.tenantId(), req.id()));
    }

    /**
     * Delete user (soft).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteUser(@Valid @RequestBody IdWithTenantReq req) {
        userManageService.deleteUser(req.tenantId(), req.id());
        return PermResult.success();
    }

    /**
     * Enable/disable user.
     */
    @PostMapping("/set-enabled")
    public PermResult<Void> setUserEnabled(@Valid @RequestBody UserSetEnabledReq req) {
        userManageService.setUserEnabled(req.tenantId(), req.userId(), req.enabled());
        return PermResult.success();
    }

    /**
     * Assign role to user.
     */
    @PostMapping("/assign-role")
    public PermResult<Void> assignRole(@Valid @RequestBody UserAssignRoleReq req) {
        userManageService.assignRole(req);
        return PermResult.success();
    }

    /**
     * Revoke role from user.
     */
    @PostMapping("/revoke-role")
    public PermResult<Void> revokeRole(@Valid @RequestBody UserRevokeRoleReq req) {
        userManageService.revokeRole(req.tenantId(), req.userId(), req.userRoleId());
        return PermResult.success();
    }

    /**
     * Get user's roles.
     */
    @PostMapping("/roles")
    public PermResult<UserRolesResp> getUserRoles(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(userManageService.getUserRoles(req.tenantId(), req.id()));
    }

    /**
     * List users (simple pagination).
     */
    @PostMapping("/list")
    public PermResult<List<UserResp>> listUsers(@Valid @RequestBody UserListReq req) {
        return PermResult.success(userManageService.listUsers(
                req.tenantId(), req.offset() != null ? req.offset() : 0, req.limit() != null ? req.limit() : 20));
    }
}
