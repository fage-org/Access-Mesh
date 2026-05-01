package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserListReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
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

    @PostMapping("/create")
    public PermResult<UserResp> createUser(@Valid @RequestBody UserCreateReq req) {
        return PermResult.success(userManageService.createUser(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/update")
    public PermResult<UserResp> updateUser(@Valid @RequestBody UserUpdateReq req) {
        return PermResult.success(userManageService.updateUser(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/detail")
    public PermResult<UserResp> getUser(@Valid @RequestBody IdReq req) {
        return PermResult.success(userManageService.getUser(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteUser(@Valid @RequestBody IdsReq req) {
        userManageService.deleteUsers(TenantContextHolder.getTenantId(), req.ids());
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<PaginatedResp<UserResp>> listUsers(@Valid @RequestBody UserListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = userManageService.countUsers(tenantId, req.subjectTypeCode(), req.domainCode(), req.keyword());
        List<UserResp> items = userManageService.listUsers(
            tenantId, req.subjectTypeCode(), req.domainCode(), req.keyword(), offset, pageSize
        );
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}

