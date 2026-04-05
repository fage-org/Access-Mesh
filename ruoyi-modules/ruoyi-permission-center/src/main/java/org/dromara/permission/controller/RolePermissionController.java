package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.RolePermissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/role-permissions")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;
    private final PermissionService permissionService;

    @PostMapping("/list")
    public R<List<RolePermissionVo>> list(@RequestBody RolePermissionListReq req) {
        return R.ok(rolePermissionService.list(req));
    }

    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody RolePermissionAddReq req) {
        permissionService.grantRolePermissions(toBatchGrantRequest(req, req.getAbstractRoleId()));
        return R.ok();
    }

    @PostMapping("/remove")
    public R<Void> remove(@Validated @RequestBody RolePermissionRemoveReq req) {
        permissionService.revokeRolePermissions(toBatchRevokeRequest(req, req.getAbstractRoleId()));
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
}
