package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.TenantIdReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserListReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSetEnabledReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Abstract user (subject) management API.
 * All APIs: POST + JSON Body. tenantId from X-Tenant-Id header.
 */
@RestController
@RequestMapping("/api/perm/abstract-user")
public class UserManageController {

    private final UserManageService userManageService;

    public UserManageController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    @PostMapping("/sync")
    public PermResult<UserResp> syncUser(@Valid @RequestBody UserSyncReq req) {
        return PermResult.success(userManageService.syncUser(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/detail")
    public PermResult<UserResp> getUser(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(userManageService.getUser(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteUser(@Valid @RequestBody IdWithTenantReq req) {
        userManageService.deleteUser(TenantContextHolder.getTenantId(), req.id());
        return PermResult.success();
    }

    @PostMapping("/set-enabled")
    public PermResult<Void> setUserEnabled(@Valid @RequestBody UserSetEnabledReq req) {
        userManageService.setUserEnabled(TenantContextHolder.getTenantId(), req.userId(), req.enabled());
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<List<UserResp>> listUsers(@Valid @RequestBody UserListReq req) {
        return PermResult.success(userManageService.listUsers(
                TenantContextHolder.getTenantId(),
                req.offset() != null ? req.offset() : 0,
                req.limit() != null ? req.limit() : 20));
    }
}

