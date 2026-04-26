package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.RoleCreateReq;
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
    public PermResult<RoleResp> getRole(@RequestParam Long tenantId, @RequestParam Long roleId) {
        return PermResult.success(roleManageService.getRole(tenantId, roleId));
    }

    /**
     * Update role basic info.
     */
    @PostMapping("/update")
    public PermResult<RoleResp> updateRole(@RequestParam Long tenantId,
                                            @RequestParam Long roleId,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) Integer sortOrder,
                                            @RequestParam(required = false) String extra) {
        return PermResult.success(roleManageService.updateRole(tenantId, roleId, name, sortOrder, extra, null));
    }

    /**
     * Delete role (soft, cascade delete children).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteRole(@RequestParam Long tenantId, @RequestParam Long roleId) {
        roleManageService.deleteRole(tenantId, roleId, null);
        return PermResult.success();
    }

    /**
     * Enable/disable role.
     */
    @PostMapping("/set-status")
    public PermResult<Void> setRoleStatus(@RequestParam Long tenantId,
                                           @RequestParam Long roleId,
                                           @RequestParam int status) {
        roleManageService.setRoleStatus(tenantId, roleId, status, null);
        return PermResult.success();
    }

    /**
     * Get role tree.
     */
    @PostMapping("/tree")
    public PermResult<List<RoleTreeResp>> getRoleTree(@RequestParam Long tenantId,
                                                       @RequestParam(required = false) Long bizDomainId) {
        return PermResult.success(roleManageService.getRoleTree(tenantId, bizDomainId));
    }

    /**
     * List roles (flat, paginated).
     */
    @PostMapping("/list")
    public PermResult<List<RoleResp>> listRoles(@RequestParam Long tenantId,
                                                 @RequestParam(defaultValue = "0") int offset,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return PermResult.success(roleManageService.listRoles(tenantId, offset, limit));
    }
}
