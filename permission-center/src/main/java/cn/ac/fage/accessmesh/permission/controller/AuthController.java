package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionTreeReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限校验控制器
 * <p>
 * 提供权限校验、资源查询、权限树查询等核心功能。
 * 该控制器是Gateway和SDK的主要调用入口，用于实时权限判定。
 * 所有接口采用POST + JSON Body方式。
 * 租户ID通过TenantContextHolder从X-Tenant-Id请求头获取。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/auth")
public class AuthController {

    private final PermissionService permissionService;

    /**
     * 构造函数注入依赖
     *
     * @param permissionService 权限服务
     */
    public AuthController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 单次权限校验
     * <p>
     * 判断用户是否有访问指定资源的权限。
     * 支持操作码校验和条件权限评估。
     * </p>
     *
     * @param req 权限校验请求，包含用户ID、资源类型、资源编码、操作码
     * @return 权限校验结果，包含是否允许、拒绝原因等
     */
    @PostMapping("/check")
    public PermResult<AuthCheckResp> check(@Valid @RequestBody AuthCheckReq req) {
        return PermResult.success(permissionService.check(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 批量权限校验
     * <p>
     * 批量判断用户对多个资源的访问权限。
     * 用于前端批量按钮权限控制等场景。
     * </p>
     *
     * @param req 批量权限校验请求，包含多个校验项
     * @return 批量校验结果，包含每个资源的校验状态
     */
    @PostMapping("/batch-check")
    public PermResult<BatchAuthCheckResp> batchCheck(@Valid @RequestBody BatchAuthCheckReq req) {
        return PermResult.success(permissionService.batchCheck(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 接口级权限校验
     * <p>
     * 专门用于Gateway的API接口权限校验。
     * 根据服务编码、HTTP方法、路径判断用户是否有访问权限。
     * </p>
     *
     * @param req 接口校验请求，包含用户ID、服务编码、HTTP方法、路径
     * @return 接口校验结果，包含是否允许访问
     */
    @PostMapping("/check-interface")
    public PermResult<CheckInterfaceResp> checkInterface(@Valid @RequestBody CheckInterfaceReq req) {
        return PermResult.success(permissionService.checkInterface(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询用户可访问资源
     * <p>
     * 查询用户在指定资源类型下有权限访问的所有资源。
     * 用于动态菜单生成、按钮权限列表等场景。
     * </p>
     *
     * @param req 资源查询请求，包含用户ID、资源类型、操作码
     * @return 用户可访问的资源列表
     */
    @PostMapping("/query-resources")
    public PermResult<QueryResourcesResp> queryResources(@Valid @RequestBody QueryResourcesReq req) {
        return PermResult.success(permissionService.queryResources(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询用户数据范围
     * <p>
     * 查询用户在指定资源上的数据范围条件。
     * 用于数据权限过滤，如只能查看本部门数据等。
     * </p>
     *
     * @param req 数据范围查询请求，包含用户ID、资源类型、资源编码
     * @return 用户的数据范围条件列表
     */
    @PostMapping("/query-scopes")
    public PermResult<QueryScopesResp> queryScopes(@Valid @RequestBody QueryScopesReq req) {
        return PermResult.success(permissionService.queryScopes(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 获取接口权限快照
     * <p>
     * 获取指定服务的所有API接口及其权限配置信息。
     * 用于Gateway缓存预热、权限配置导出等场景。
     * </p>
     *
     * @param req 接口快照请求，包含服务编码
     * @return 服务接口权限配置列表
     */
    @PostMapping("/interface-snapshot")
    public PermResult<InterfaceSnapshotResp> interfaceSnapshot(@Valid @RequestBody InterfaceSnapshotReq req) {
        return PermResult.success(permissionService.interfaceSnapshot(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询权限树
     * <p>
     * 查询用户在指定资源类型下的权限树结构。
     * 返回带有权限标记的资源树，用于前端权限配置展示。
     * </p>
     *
     * @param req 权限树查询请求，包含用户ID、资源类型、域编码
     * @return 带权限标记的资源树
     */
    @PostMapping("/query-permission-tree")
    public PermResult<PermissionTreeResp> queryPermissionTree(@Valid @RequestBody PermissionTreeReq req) {
        return PermResult.success(permissionService.queryPermissionTree(TenantContextHolder.getTenantId(), req));
    }
}