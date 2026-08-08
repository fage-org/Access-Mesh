package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionChildrenReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionRemoveChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemsResp;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 角色资源权限管理控制器
 * <p>
 * 提供角色权限的授予、撤销、查询等功能。
 * 角色权限定义了角色可以访问的资源及其操作权限。
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
    public PermResult<RolePermissionItemsResp> applyGrantPlan(
            @Valid @RequestBody ApplyGrantPlanReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.applyGrantPlan(
            TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    /**
     * 批量授予角色权限
     * <p>
     * 为角色批量授予多个资源的访问权限。
     * 支持操作权限位运算和条件权限配置。
     * </p>
     *
     * @param req 角色权限授予请求，包含角色ID和权限条目列表
     * @return 授予成功的权限条目列表
     */
    @PostMapping("/save")
    public PermResult<RolePermissionItemsResp> batchGrant(@Valid @RequestBody RoleGrantReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.batchGrant(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    /**
     * 批量撤销角色权限
     * <p>
     * 批量撤销角色的权限配置。
     * </p>
     *
     * @param req 批量撤销请求，包含权限ID列表
     * @return 操作成功结果
     */
    @PostMapping("/revoke")
    public PermResult<Void> batchRevoke(@Valid @RequestBody BatchRevokeReq req) {
        permissionGrantService.batchRevoke(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
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
    public PermResult<RolePermissionItemsResp> list(@Valid @RequestBody RolePermissionListReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.listPermissions(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    /**
     * 查询权限的子权限列表
     * <p>
     * 查询指定权限条目下的子权限（依赖该权限的权限）。
     * 用于权限树展开和级联操作。
     * </p>
     *
     * @param req 子权限查询请求，包含父权限ID
     * @return 子权限条目列表
     */
    @PostMapping("/children")
    public PermResult<RolePermissionItemsResp> children(@Valid @RequestBody RolePermissionChildrenReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.listChildren(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    /**
     * 添加子权限
     * <p>
     * 在指定父权限下添加子权限条目。
     * 子权限依赖于父权限，父权限撤销时子权限也会被级联删除。
     * </p>
     *
     * @param req 添加子权限请求，包含父权限ID和子权限配置
     * @return 添加成功的子权限列表
     */
    @PostMapping("/add-child")
    public PermResult<RolePermissionItemsResp> addChild(@Valid @RequestBody RolePermissionAddChildReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.addChildren(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    /**
     * 移除子权限
     * <p>
     * 移除指定的子权限条目。
     * </p>
     *
     * @param req 移除子权限请求，包含子权限ID
     * @return 操作成功结果
     */
    @PostMapping("/remove-child")
    public PermResult<Void> removeChild(@Valid @RequestBody RolePermissionRemoveChildReq req) {
        permissionGrantService.removeChild(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }
}
