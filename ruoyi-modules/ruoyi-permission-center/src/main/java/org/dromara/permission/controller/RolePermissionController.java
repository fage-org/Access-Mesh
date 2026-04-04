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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色-资源-操作权限 role_resource_permission 接口
 */
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
        RolePermissionBatchGrantRequest request = new RolePermissionBatchGrantRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractRoleId(req.getAbstractRoleId());
        request.setItems(req.getItems().stream().map(item -> {
            RolePermissionBatchGrantRequest.RolePermissionGrantItem mapped = new RolePermissionBatchGrantRequest.RolePermissionGrantItem();
            mapped.setResourceEntityId(item.getResourceEntityId());
            mapped.setOperationPermissionId(item.getOperationPermissionId());
            mapped.setCanManage(item.getCanManage());
            mapped.setConditionId(item.getConditionId());
            return mapped;
        }).toList());
        permissionService.grantRolePermissions(request);
        return R.ok();
    }

    @PostMapping("/remove")
    public R<Void> remove(@Validated @RequestBody RolePermissionRemoveReq req) {
        RolePermissionBatchRevokeRequest request = new RolePermissionBatchRevokeRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractRoleId(req.getAbstractRoleId());
        request.setItems(req.getItems().stream().map(item -> {
            RolePermissionBatchRevokeRequest.RolePermissionRevokeItem mapped = new RolePermissionBatchRevokeRequest.RolePermissionRevokeItem();
            mapped.setResourceEntityId(item.getResourceEntityId());
            mapped.setOperationPermissionId(item.getOperationPermissionId());
            return mapped;
        }).toList());
        permissionService.revokeRolePermissions(request);
        return R.ok();
    }
}
