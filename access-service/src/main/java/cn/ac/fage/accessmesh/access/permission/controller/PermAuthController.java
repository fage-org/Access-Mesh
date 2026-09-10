package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.access.permission.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.permission.service.PermissionQueryAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限校验控制器
 * <p>
 * 提供权限校验、资源查询等核心功能。
 * 该控制器是Gateway和SDK的主要调用入口，用于实时权限判定。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/auth")
public class PermAuthController {

    private final PermissionCheckAppService permissionCheckAppService;
    private final PermissionQueryAppService permissionQueryAppService;

    /**
     * 构造函数注入依赖
     *
     * @param permissionCheckAppService 权限检查服务
     * @param permissionQueryAppService 权限查询服务
     */
    public PermAuthController(PermissionCheckAppService permissionCheckAppService,
                          PermissionQueryAppService permissionQueryAppService) {
        this.permissionCheckAppService = permissionCheckAppService;
        this.permissionQueryAppService = permissionQueryAppService;
    }

    /**
     * 单次权限校验
     *
     * @param req 权限校验请求，包含用户ID、资源类型、资源编码、操作码
     * @return 权限校验结果，包含是否允许、拒绝原因等
     */
    @PostMapping("/check")
    public R<AuthCheckResp> check(@Valid @RequestBody AuthCheckReq req) {
        return R.ok(permissionCheckAppService.check(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 批量权限校验
     *
     * @param req 批量权限校验请求，包含多个校验项
     * @return 批量校验结果，包含每个资源的校验状态
     */
    @PostMapping("/batch-check")
    public R<BatchAuthCheckResp> batchCheck(@Valid @RequestBody BatchAuthCheckReq req) {
        return R.ok(permissionCheckAppService.batchCheck(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 接口级权限校验
     *
     * @param req 接口校验请求，包含用户ID、服务编码、HTTP方法、路径
     * @return 接口校验结果，包含是否允许访问
     */
    @PostMapping("/check-interface")
    public R<CheckInterfaceResp> checkInterface(@Valid @RequestBody CheckInterfaceReq req) {
        return R.ok(permissionCheckAppService.checkInterface(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询用户可访问资源
     *
     * @param req 资源查询请求，包含用户ID、资源类型、操作码
     * @return 用户可访问的资源列表
     */
    @PostMapping("/query-resources")
    public R<QueryResourcesResp> queryResources(@Valid @RequestBody QueryResourcesReq req) {
        return R.ok(permissionQueryAppService.queryResources(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询用户数据范围
     *
     * @param req 数据范围查询请求，包含用户ID、资源类型、资源编码
     * @return 用户的数据范围条件列表
     */
    @PostMapping("/query-scopes")
    public R<QueryScopesResp> queryScopes(@Valid @RequestBody QueryScopesReq req) {
        return R.ok(permissionQueryAppService.queryScopes(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 获取接口权限快照
     *
     * @param req 接口快照请求，包含主体、服务编码和可选权限令牌
     * @return 主体维度的服务接口权限快照
     */
    @PostMapping("/interface-snapshot")
    public R<InterfaceSnapshotResp> interfaceSnapshot(@Valid @RequestBody InterfaceSnapshotReq req) {
        return R.ok(permissionQueryAppService.interfaceSnapshot(TenantContextHolder.getTenantId(), req));
    }

}
