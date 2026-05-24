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
import cn.ac.fage.accessmesh.permission.service.LogQueryAppService;
import cn.ac.fage.accessmesh.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限视图控制器
 * <p>
 * 提供权限的可视化查询功能，用于管理界面展示。
 * 包括用户有效权限、资源权限分布、角色权限配置、权限变更历史等查询。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/permission-view")
public class PermissionViewController {

    private final PermissionViewAppService permissionViewAppService;
    private final TypeResolutionService typeResolutionService;
    private final LogQueryAppService logQueryService;

    public PermissionViewController(PermissionViewAppService permissionViewAppService,
                                    TypeResolutionService typeResolutionService,
                                    LogQueryAppService logQueryService) {
        this.permissionViewAppService = permissionViewAppService;
        this.typeResolutionService = typeResolutionService;
        this.logQueryService = logQueryService;
    }

    @PostMapping("/effective-permissions")
    public PermResult<PermissionEffectivePermissionsResp> getEffectivePermissions(@Valid @RequestBody UserPermissionViewReq req) {
        return PermResult.success(permissionViewAppService.getEffectivePermissions(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/resource-users")
    public PermResult<ResourcePermissionViewResp> getResourcePermissions(@Valid @RequestBody ResourcePermissionViewReq req) {
        return PermResult.success(permissionViewAppService.getResourcePermissions(
            TenantContextHolder.getTenantId(), req.domainCode(), req.resourceTypeCode(), req.resourceCode(), req.codeType()));
    }

    @PostMapping("/role-permissions")
    public PermResult<RolePermissionViewResp> getRolePermissions(@Valid @RequestBody RolePermissionViewReq req) {
        return PermResult.success(permissionViewAppService.getRolePermissions(
            TenantContextHolder.getTenantId(), req.domainCode(), req.roleTypeCode(), req.roleExternalId(), req.expandSub() != null && req.expandSub()));
    }

    @PostMapping("/effective-roles")
    public PermResult<ItemsResp<EffectiveRoleResp>> getEffectiveRoles(@Valid @RequestBody UserEffectiveRolesReq req) {
        return PermResult.success(permissionViewAppService.listEffectiveRoles(
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
            permissionViewAppService.getUserResourceTree(tenantId, userId, req)));
    }

    @PostMapping("/explain")
    public PermResult<PermissionExplainResp> explain(@Valid @RequestBody PermissionExplainReq req) {
        return PermResult.success(permissionViewAppService.explain(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/recent-changes")
    public PermResult<PermissionRecentChangesResp> recentChanges(@Valid @RequestBody PermissionRecentChangesReq req) {
        return PermResult.success(logQueryService.getRecentChanges(TenantContextHolder.getTenantId(), req));
    }
}
