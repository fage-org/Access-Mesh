package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.UserRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/user-roles")
public class UserRoleController {

    private final UserRoleService userRoleService;
    private final PermissionService permissionService;

    @PostMapping("/list")
    public R<List<UserRoleVo>> list(@RequestBody UserRoleListReq req) {
        return R.ok(userRoleService.list(req));
    }

    @PostMapping("/assign")
    public R<Void> assign(@Validated @RequestBody UserRoleAssignReq req) {
        permissionService.assignUserRoles(toBatchAssignRequest(req, req.getAbstractUserId()));
        return R.ok();
    }

    @PostMapping("/revoke")
    public R<Void> revoke(@Validated @RequestBody UserRoleRevokeReq req) {
        permissionService.revokeUserRoles(toBatchRevokeRequest(req, req.getAbstractUserId()));
        return R.ok();
    }

    static UserRoleBatchAssignRequest toBatchAssignRequest(UserRoleAssignReq req, Long userId) {
        UserRoleBatchAssignRequest request = new UserRoleBatchAssignRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractUserId(userId);
        request.setRequestId(req.getRequestId());
        request.setChangeSource(req.getChangeSource());
        request.setChangeReason(req.getChangeReason());
        request.setRoleIds(req.getRoleIds());
        request.setValidFrom(req.getValidFrom());
        request.setValidTo(req.getValidTo());
        return request;
    }

    static UserRoleBatchRevokeRequest toBatchRevokeRequest(UserRoleRevokeReq req, Long userId) {
        UserRoleBatchRevokeRequest request = new UserRoleBatchRevokeRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractUserId(userId);
        request.setRequestId(req.getRequestId());
        request.setChangeSource(req.getChangeSource());
        request.setChangeReason(req.getChangeReason());
        request.setRoleIds(req.getRoleIds());
        return request;
    }
}
