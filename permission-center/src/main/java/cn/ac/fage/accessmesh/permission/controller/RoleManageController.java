package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.ListWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleStatusReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RoleTreeResp;
import cn.ac.fage.accessmesh.permission.service.RoleManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/role")
public class RoleManageController {

    private final RoleManageService roleManageService;

    public RoleManageController(RoleManageService roleManageService) {
        this.roleManageService = roleManageService;
    }

    /**
     * Create a role.
     */
    @PostMapping("/create")
    public PermResult<RoleResp> createRole(@Valid @RequestBody RoleCreateReq req) {
        return PermResult.success(roleManageService.createRole(req, null));
    }

    /**
     * Get role by ID.
     */
    @PostMapping("/get")
    public PermResult<RoleResp> getRole(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(roleManageService.getRole(req.tenantId(), req.id()));
    }

    /**
     * Update role basic info.
     */
    @PostMapping("/update")
    public PermResult<RoleResp> updateRole(@Valid @RequestBody RoleUpdateReq req) {
        return PermResult.success(roleManageService.updateRole(
                req.tenantId(), req.roleId(), req.name(), req.sortOrder(), req.extra(), null));
    }

    /**
     * Delete role (soft, cascade delete children).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteRole(@Valid @RequestBody IdWithTenantReq req) {
        roleManageService.deleteRole(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    /**
     * Enable/disable role.
     */
    @PostMapping("/set-status")
    public PermResult<Void> setRoleStatus(@Valid @RequestBody RoleStatusReq req) {
        roleManageService.setRoleStatus(req.tenantId(), req.roleId(), req.status(), null);
        return PermResult.success();
    }

    /**
     * Get role tree.
     */
    @PostMapping("/tree")
    public PermResult<List<RoleTreeResp>> getRoleTree(@Valid @RequestBody ListWithTenantReq req) {
        return PermResult.success(roleManageService.getRoleTree(req.tenantId(), req.filterValue() != null ? req.filterValue().longValue() : null));
    }

    /**
     * List roles (flat, paginated).
     */
    @PostMapping("/list")
    public PermResult<List<RoleResp>> listRoles(@Valid @RequestBody RoleListReq req) {
        return PermResult.success(roleManageService.listRoles(req.tenantId(), req.offset() != null ? req.offset() : 0, req.limit() != null ? req.limit() : 20));
    }
}
