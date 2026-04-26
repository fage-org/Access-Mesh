package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
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
    public PermResult<UserResp> getUser(@RequestParam Long tenantId, @RequestParam Long userId) {
        return PermResult.success(userManageService.getUser(tenantId, userId));
    }

    /**
     * Delete user (soft).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteUser(@RequestParam Long tenantId, @RequestParam Long userId) {
        userManageService.deleteUser(tenantId, userId);
        return PermResult.success();
    }

    /**
     * Enable/disable user.
     */
    @PostMapping("/set-enabled")
    public PermResult<Void> setUserEnabled(@RequestParam Long tenantId,
                                            @RequestParam Long userId,
                                            @RequestParam boolean enabled) {
        userManageService.setUserEnabled(tenantId, userId, enabled);
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
    public PermResult<Void> revokeRole(@RequestParam Long tenantId,
                                        @RequestParam Long userId,
                                        @RequestParam Long userRoleId) {
        userManageService.revokeRole(tenantId, userId, userRoleId);
        return PermResult.success();
    }

    /**
     * Get user's roles.
     */
    @PostMapping("/roles")
    public PermResult<UserRolesResp> getUserRoles(@RequestParam Long tenantId, @RequestParam Long userId) {
        return PermResult.success(userManageService.getUserRoles(tenantId, userId));
    }

    /**
     * List users (simple pagination).
     */
    @PostMapping("/list")
    public PermResult<List<UserResp>> listUsers(@RequestParam Long tenantId,
                                                 @RequestParam(defaultValue = "0") int offset,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return PermResult.success(userManageService.listUsers(tenantId, offset, limit));
    }
}
