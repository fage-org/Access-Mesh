package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.SubPermAllowedTypesReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SubPermAllowedTypesResp;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 角色资源权限管理控制器
 * <p>
 * 提供角色权限的查询与聚合授予功能。
 * 角色权限定义了角色可以访问的资源及其操作权限。
 * 写入唯一入口为 apply-grant-plan（记录级 plan 单事务原子执行）；
 * 旧写入口 save/revoke/children/add-child/remove-child 已随 T-PERM-034 删除（2026-08-27，
 * 端点退役收口：无存量调用方，删除语义由 apply-grant-plan 的 creates/updates/removes 覆盖）。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/role-resource-permission")
public class PermissionGrantController {

    private final PermissionGrantAppService permissionGrantService;

    /**
     * 构造函数注入依赖
     *
     * @param permissionGrantService 权限授予服务
     */
    public PermissionGrantController(PermissionGrantAppService permissionGrantService) {
        this.permissionGrantService = permissionGrantService;
    }

    /**
     * 单事务应用授权计划。
     */
    @PostMapping("/apply-grant-plan")
    public R<RolePermissionItemsResp> applyGrantPlan(
            @Valid @RequestBody ApplyGrantPlanReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.applyGrantPlan(
            TenantContextHolder.getTenantId(), req);
        return R.ok(new RolePermissionItemsResp(items));
    }

    /**
     * 查询角色的权限列表
     * <p>
     * 查询角色当前配置的所有权限条目。
     * </p>
     *
     * @param req 权限列表查询请求，包含角色ID
     * @return 角色权限条目列表
     */
    @PostMapping("/list")
    public R<RolePermissionItemsResp> list(@Valid @RequestBody RolePermissionListReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.listPermissions(TenantContextHolder.getTenantId(), req);
        return R.ok(new RolePermissionItemsResp(items));
    }

    /**
     * 子权限允许类型只读查询（api-contract §6.5.2）
     * <p>
     * 按父资源类型返回 SUB_PERM 允许的子资源类型（授权弹窗子权限配置器数据源）；
     * 判定口径与写链路 SUB_PERM fail-closed 校验同源（resolveSubPermissionPolicy）。
     * </p>
     */
    @PostMapping("/sub-perm-allowed-types")
    public R<SubPermAllowedTypesResp> subPermAllowedTypes(
            @Valid @RequestBody SubPermAllowedTypesReq req) {
        return R.ok(permissionGrantService.subPermAllowedTypes(
            TenantContextHolder.getTenantId(), req));
    }
}
