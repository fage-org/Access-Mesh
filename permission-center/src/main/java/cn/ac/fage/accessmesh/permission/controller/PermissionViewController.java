package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourcePermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserEffectiveRolesReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.permission.dto.resp.EffectiveRoleResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionRecentChangesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permission View APIs — read-only visibility for user/resource/role permissions.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/permission-view")
public class PermissionViewController {

    private final PermissionViewService permissionViewService;
    private final TypeResolutionService typeResolutionService;

    public PermissionViewController(PermissionViewService permissionViewService,
                                    TypeResolutionService typeResolutionService) {
        this.permissionViewService = permissionViewService;
        this.typeResolutionService = typeResolutionService;
    }

    @PostMapping("/effective-permissions")
    public PermResult<PermissionEffectivePermissionsResp<?>> getUserPermissions(@Valid @RequestBody UserPermissionViewReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = Math.min(PageUtil.pageSize(req.pageSize(), 50), 200);
        if ("USER".equalsIgnoreCase(req.targetType())) {
            Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
            if (userId == null) {
                return PermResult.success(new PermissionEffectivePermissionsResp<>("USER", List.of(), 0, pageNum, pageSize, false));
            }
            var paged = permissionViewService.getUserPermissionsWithFilters(tenantId, userId, req);
            return PermResult.success(new PermissionEffectivePermissionsResp<>(
                "USER", paged.items(), (int) paged.total(), pageNum, pageSize, paged.hasNext()));
        }
        var paged = permissionViewService.getRolePermissionItemsPaged(
            tenantId, req.domainCode(), req.roleTypeCode(), req.roleExternalId(), pageNum, pageSize);
        return PermResult.success(new PermissionEffectivePermissionsResp<>(
            "ROLE", paged.items(), (int) paged.total(), pageNum, pageSize, paged.hasNext()));
    }

    @PostMapping("/resource-users")
    public PermResult<ResourcePermissionViewResp> getResourcePermissions(@Valid @RequestBody ResourcePermissionViewReq req) {
        return PermResult.success(permissionViewService.getResourcePermissions(
            TenantContextHolder.getTenantId(), req.domainCode(), req.resourceTypeCode(), req.resourceCode(), req.codeType()));
    }

    @PostMapping("/role-permissions")
    public PermResult<RolePermissionViewResp> getRolePermissions(@Valid @RequestBody RolePermissionViewReq req) {
        return PermResult.success(permissionViewService.getRolePermissions(
            TenantContextHolder.getTenantId(), req.domainCode(), req.roleTypeCode(), req.roleExternalId(), req.expandSub() != null && req.expandSub()));
    }

    @PostMapping("/effective-roles")
    public PermResult<ItemsResp<EffectiveRoleResp>> getEffectiveRoles(@Valid @RequestBody UserEffectiveRolesReq req) {
        return PermResult.success(permissionViewService.listEffectiveRoles(
            TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/resource-tree")
    public PermResult<ItemsResp<ResourcePermissionTreeResp>> getResourceTree(@Valid @RequestBody UserResourceTreeReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return PermResult.success(new ItemsResp<>(List.of()));
        }
        return PermResult.success(new ItemsResp<>(
            permissionViewService.getUserResourceTree(tenantId, userId, req)));
    }

    @PostMapping("/explain")
    public PermResult<PermissionExplainResp> explain(@Valid @RequestBody PermissionExplainReq req) {
        return PermResult.success(permissionViewService.explain(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/recent-changes")
    public PermResult<PermissionRecentChangesResp> recentChanges(@Valid @RequestBody PermissionRecentChangesReq req) {
        return PermResult.success(permissionViewService.recentChanges(TenantContextHolder.getTenantId(), req));
    }
}
