package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限视图控制器
 * <p>
 * 原权限排查视图七端点（effective-permissions/resource-users/role-permissions/
 * effective-roles/resource-tree/explain/recent-changes）已删除（T-PERM-059，
 * 2026-09-10 删除重设计定案）；本控制器仅保留登录权限串端点。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/permission-view")
public class PermissionViewController {

    private final PermissionViewAppService permissionViewAppService;

    public PermissionViewController(PermissionViewAppService permissionViewAppService) {
        this.permissionViewAppService = permissionViewAppService;
    }

    /**
     * 用户有效权限码聚合下发（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 不分页、扁平 perm 串列表，可供前端 hasPerms、功能开关、客户端能力下发等场景使用。
     * 门禁：自查豁免；查他人需操作者对被查用户有 {@code USER:VIEW}。
     */
    @PostMapping("/effective-permission-codes")
    public R<UserEffectivePermissionCodesResp> getEffectivePermissionCodes(
            @Valid @RequestBody UserEffectivePermissionCodesReq req) {
        return R.ok(permissionViewAppService.getEffectivePermissionCodesForManage(
            TenantContextHolder.getTenantId(), req));
    }
}
