package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationListReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.permission.service.OperationManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operation Permission management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/operation-permission")
public class OperationManageController {

    private final OperationManageService operationManageService;

    public OperationManageController(OperationManageService operationManageService) {
        this.operationManageService = operationManageService;
    }

    /**
     * Create an operation permission.
     */
    @PostMapping("/create")
    public PermResult<OperationPermissionResp> createOperation(@Valid @RequestBody OperationCreateReq req) {
        return PermResult.success(operationManageService.createOperation(
                TenantContextHolder.getTenantId(), req.resourceTypeCode(), req.code(), req.name(), req.binaryBit(), req.inheritMask(), null));
    }

    /**
     * Get operation by ID.
     */
    @PostMapping("/detail")
    public PermResult<OperationPermissionResp> getOperation(@Valid @RequestBody IdReq req) {
        return PermResult.success(operationManageService.getOperation(TenantContextHolder.getTenantId(), req.id()));
    }

    /**
     * List operations (optionally filtered by resourceType).
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<OperationPermissionResp>> listOperations(@Valid @RequestBody OperationListReq req) {
        return PermResult.success(new ItemsResp<>(
            operationManageService.listOperations(TenantContextHolder.getTenantId(), req.resourceTypeCode())
        ));
    }

    /**
     * Update operation.
     */
    @PostMapping("/update")
    public PermResult<OperationPermissionResp> updateOperation(@Valid @RequestBody OperationUpdateReq req) {
        return PermResult.success(operationManageService.updateOperation(
                TenantContextHolder.getTenantId(), req.operationId(), req.name(), req.binaryBit(), req.inheritMask(), null));
    }

    /**
     * Delete operation (soft).
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteOperation(@Valid @RequestBody IdsReq req) {
        operationManageService.deleteOperations(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }
}
