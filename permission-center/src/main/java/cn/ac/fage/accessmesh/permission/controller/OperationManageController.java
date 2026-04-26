package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationListReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.permission.service.OperationManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operation Permission management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/operation")
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
                req.tenantId(), req.resourceType(), req.code(), req.name(), req.binaryBit(), req.inheritMask(), null));
    }

    /**
     * Get operation by ID.
     */
    @PostMapping("/get")
    public PermResult<OperationPermissionResp> getOperation(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(operationManageService.getOperation(req.tenantId(), req.id()));
    }

    /**
     * List operations (optionally filtered by resourceType).
     */
    @PostMapping("/list")
    public PermResult<List<OperationPermissionResp>> listOperations(@Valid @RequestBody OperationListReq req) {
        return PermResult.success(operationManageService.listOperations(req.tenantId(), req.resourceType()));
    }

    /**
     * Update operation.
     */
    @PostMapping("/update")
    public PermResult<OperationPermissionResp> updateOperation(@Valid @RequestBody OperationUpdateReq req) {
        return PermResult.success(operationManageService.updateOperation(
                req.tenantId(), req.operationId(), req.name(), req.binaryBit(), req.inheritMask(), null));
    }

    /**
     * Delete operation (soft).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteOperation(@Valid @RequestBody IdWithTenantReq req) {
        operationManageService.deleteOperation(req.tenantId(), req.id(), null);
        return PermResult.success();
    }
}
