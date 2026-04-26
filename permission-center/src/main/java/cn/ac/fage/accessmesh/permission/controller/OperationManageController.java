package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.permission.service.OperationManageService;
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
    public PermResult<OperationPermissionResp> createOperation(
            @RequestParam Long tenantId,
            @RequestParam Integer resourceType,
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam Long binaryBit,
            @RequestParam(required = false, defaultValue = "0") Long inheritMask) {
        return PermResult.success(operationManageService.createOperation(tenantId, resourceType, code, name, binaryBit, inheritMask, null));
    }

    /**
     * Get operation by ID.
     */
    @PostMapping("/get")
    public PermResult<OperationPermissionResp> getOperation(@RequestParam Long tenantId, @RequestParam Long operationId) {
        return PermResult.success(operationManageService.getOperation(tenantId, operationId));
    }

    /**
     * List operations (optionally filtered by resourceType).
     */
    @PostMapping("/list")
    public PermResult<List<OperationPermissionResp>> listOperations(
            @RequestParam Long tenantId,
            @RequestParam(required = false) Integer resourceType) {
        return PermResult.success(operationManageService.listOperations(tenantId, resourceType));
    }

    /**
     * Update operation.
     */
    @PostMapping("/update")
    public PermResult<OperationPermissionResp> updateOperation(
            @RequestParam Long tenantId,
            @RequestParam Long operationId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long binaryBit,
            @RequestParam(required = false) Long inheritMask) {
        return PermResult.success(operationManageService.updateOperation(tenantId, operationId, name, binaryBit, inheritMask, null));
    }

    /**
     * Delete operation (soft).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteOperation(@RequestParam Long tenantId, @RequestParam Long operationId) {
        operationManageService.deleteOperation(tenantId, operationId, null);
        return PermResult.success();
    }
}
