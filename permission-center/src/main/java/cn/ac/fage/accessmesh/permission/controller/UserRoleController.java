package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRevokeRoleReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-role relationship management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/user-role")
public class UserRoleController {

    private final UserManageService userManageService;

    public UserRoleController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    @PostMapping("/assign")
    public PermResult<Void> assignRole(@Valid @RequestBody UserAssignRoleReq req) {
        userManageService.assignRole(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    @PostMapping("/revoke")
    public PermResult<Void> revokeRole(@Valid @RequestBody UserRevokeRoleReq req) {
        userManageService.revokeRole(TenantContextHolder.getTenantId(), req.userId(), req.userRoleId());
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<UserRolesResp> getUserRoles(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(userManageService.getUserRoles(TenantContextHolder.getTenantId(), req.id()));
    }
}
