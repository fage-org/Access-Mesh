package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
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
        permissionService.grantRolePermissions(toBatchGrantRequest(req, roleId));
        return R.ok();
    }

    @DeleteMapping
    public R<Void> revoke(@PathVariable("roleId") Long roleId, @Validated @RequestBody RolePermissionRemoveReq req) {
        ensureRoleId(roleId, req.getAbstractRoleId());
        permissionService.revokeRolePermissions(toBatchRevokeRequest(req, roleId));
        return R.ok();
    }

    static RolePermissionBatchGrantRequest toBatchGrantRequest(RolePermissionAddReq req, Long roleId) {
        RolePermissionBatchGrantRequest request = new RolePermissionBatchGrantRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractRoleId(roleId);
        request.setRequestId(req.getRequestId());
        request.setChangeSource(req.getChangeSource());
        request.setChangeReason(req.getChangeReason());
        request.setItems(req.getItems().stream().map(item -> {
            RolePermissionBatchGrantRequest.RolePermissionGrantItem mapped = new RolePermissionBatchGrantRequest.RolePermissionGrantItem();
            mapped.setResourceEntityId(item.getResourceEntityId());
            mapped.setOperationPermissionId(item.getOperationPermissionId());
            mapped.setCanManage(item.getCanManage());
            mapped.setConditionId(item.getConditionId());
            return mapped;
        }).toList());
        return request;
    }

    static RolePermissionBatchRevokeRequest toBatchRevokeRequest(RolePermissionRemoveReq req, Long roleId) {
        RolePermissionBatchRevokeRequest request = new RolePermissionBatchRevokeRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractRoleId(roleId);
        request.setRequestId(req.getRequestId());
        request.setChangeSource(req.getChangeSource());
        request.setChangeReason(req.getChangeReason());
        request.setItems(req.getItems().stream().map(item -> {
            RolePermissionBatchRevokeRequest.RolePermissionRevokeItem mapped = new RolePermissionBatchRevokeRequest.RolePermissionRevokeItem();
            mapped.setResourceEntityId(item.getResourceEntityId());
            mapped.setOperationPermissionId(item.getOperationPermissionId());
            return mapped;
        }).toList());
        return request;
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
