package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.RolePermissionService;
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
@RequestMapping("/api/perm/roles/{roleId}/permissions")
public class RolePermissionContractController {

    private final RolePermissionService rolePermissionService;
    private final PermissionService permissionService;

    @GetMapping
    public R<List<RolePermissionVo>> list(@PathVariable("roleId") Long roleId,
                                          @RequestParam("tenantId") Long tenantId) {
        RolePermissionListReq req = new RolePermissionListReq();
        req.setTenantId(tenantId);
        req.setAbstractRoleId(roleId);
        return R.ok(rolePermissionService.list(req));
    }

    @PostMapping
    public R<Void> grant(@PathVariable("roleId") Long roleId, @Validated @RequestBody RolePermissionAddReq req) {
        ensureRoleId(roleId, req.getAbstractRoleId());
        permissionService.grantRolePermissions(RolePermissionController.toBatchGrantRequest(req, roleId));
        return R.ok();
    }

    @DeleteMapping
    public R<Void> revoke(@PathVariable("roleId") Long roleId, @Validated @RequestBody RolePermissionRemoveReq req) {
        ensureRoleId(roleId, req.getAbstractRoleId());
        permissionService.revokeRolePermissions(RolePermissionController.toBatchRevokeRequest(req, roleId));
        return R.ok();
    }

    private void ensureRoleId(Long pathRoleId, Long bodyRoleId) {
        if (pathRoleId == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        if (bodyRoleId != null && !pathRoleId.equals(bodyRoleId)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "roleId mismatch");
        }
    }
}
