package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.UserRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/users/{userId}/roles")
public class UserRoleContractController {

    private final UserRoleService userRoleService;
    private final PermissionService permissionService;

    @GetMapping
    public R<List<UserRoleVo>> list(@PathVariable("userId") Long userId,
                                    @RequestParam("tenantId") Long tenantId) {
        UserRoleListReq req = new UserRoleListReq();
        req.setTenantId(tenantId);
        req.setAbstractUserId(userId);
        return R.ok(userRoleService.list(req));
    }

    @PostMapping
    public R<Void> assign(@PathVariable("userId") Long userId, @Validated @RequestBody UserRoleAssignReq req) {
        ensureUserId(userId, req.getAbstractUserId());
        permissionService.assignUserRoles(toBatchAssignRequest(req, userId));
        return R.ok();
    }

    @DeleteMapping
    public R<Void> revoke(@PathVariable("userId") Long userId, @Validated @RequestBody UserRoleRevokeReq req) {
        ensureUserId(userId, req.getAbstractUserId());
        permissionService.revokeUserRoles(toBatchRevokeRequest(req, userId));
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

    private void ensureUserId(Long pathUserId, Long bodyUserId) {
        if (pathUserId == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        if (bodyUserId != null && !pathUserId.equals(bodyUserId)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "userId mismatch");
        }
    }
}
