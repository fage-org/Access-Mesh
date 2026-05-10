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
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
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

    private final PermissionViewService permissionViewService;
    private final TypeResolutionService typeResolutionService;

    /**
     * 构造函数注入依赖
     *
     * @param permissionViewService 权限视图服务
     * @param typeResolutionService 类型解析服务，用于外部ID到内部ID的转换
     */
    public PermissionViewController(PermissionViewService permissionViewService,
                                    TypeResolutionService typeResolutionService) {
        this.permissionViewService = permissionViewService;
        this.typeResolutionService = typeResolutionService;
    }

    /**
     * 查询用户的有效权限
     * <p>
     * 查询用户当前生效的所有权限，包括直接分配和角色继承的权限。
     * 返回权限的详细信息，包括资源、操作、条件等。
     * </p>
     *
     * @param req 用户权限视图请求，包含用户ID和域编码
     * @return 用户有效权限详情
     */
    @PostMapping("/effective-permissions")
    public PermResult<PermissionEffectivePermissionsResp> getEffectivePermissions(@Valid @RequestBody UserPermissionViewReq req) {
        return PermResult.success(permissionViewService.getEffectivePermissions(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询资源的权限分布
     * <p>
     * 查询指定资源上所有用户的权限分布情况。
     * 用于资源权限管理界面展示。
     * </p>
     *
     * @param req 资源权限视图请求，包含资源类型、资源编码、域编码
     * @return 资源权限分布详情
     */
    @PostMapping("/resource-users")
    public PermResult<ResourcePermissionViewResp> getResourcePermissions(@Valid @RequestBody ResourcePermissionViewReq req) {
        return PermResult.success(permissionViewService.getResourcePermissions(
            TenantContextHolder.getTenantId(), req.domainCode(), req.resourceTypeCode(), req.resourceCode(), req.codeType()));
    }

    /**
     * 查询角色的权限配置
     * <p>
     * 查询角色当前配置的所有权限条目。
     * 支持展开子角色权限（expandSub=true时显示角色继承的子角色权限）。
     * </p>
     *
     * @param req 角色权限视图请求，包含角色类型、角色外部ID、域编码
     * @return 角色权限配置详情
     */
    @PostMapping("/role-permissions")
    public PermResult<RolePermissionViewResp> getRolePermissions(@Valid @RequestBody RolePermissionViewReq req) {
        return PermResult.success(permissionViewService.getRolePermissions(
            TenantContextHolder.getTenantId(), req.domainCode(), req.roleTypeCode(), req.roleExternalId(), req.expandSub() != null && req.expandSub()));
    }

    /**
     * 查询用户的有效角色列表
     * <p>
     * 查询用户当前生效的所有角色，包括直接分配和组角色继承的角色。
     * 返回角色的详细信息，包括角色名称、类型、状态等。
     * </p>
     *
     * @param req 用户有效角色查询请求，包含用户ID和域编码
     * @return 用户有效角色列表
     */
    @PostMapping("/effective-roles")
    public PermResult<ItemsResp<EffectiveRoleResp>> getEffectiveRoles(@Valid @RequestBody UserEffectiveRolesReq req) {
        return PermResult.success(permissionViewService.listEffectiveRoles(
            TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询用户的资源权限树
     * <p>
     * 查询用户在指定资源类型下的权限树结构。
     * 返回带有权限标记的资源树，用于前端权限配置展示。
     * </p>
     *
     * @param req 用户资源树请求，包含主体类型、外部ID、资源类型
     * @return 带权限标记的资源树列表
     */
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

    /**
     * 权限解释查询
     * <p>
     * 解释用户为什么能或不能访问某个资源。
     * 返回权限来源链路（直接分配、角色继承等）和条件详情。
     * </p>
     *
     * @param req 权限解释请求，包含用户ID、资源类型、资源编码、操作码
     * @return 权限解释详情
     */
    @PostMapping("/explain")
    public PermResult<PermissionExplainResp> explain(@Valid @RequestBody PermissionExplainReq req) {
        return PermResult.success(permissionViewService.explain(TenantContextHolder.getTenantId(), req));
    }

    /**
     * 查询权限最近变更历史
     * <p>
     * 查询指定角色或用户最近的权限变更记录。
     * 用于权限审计和变更追踪。
     * </p>
     *
     * @param req 权限变更历史请求，包含角色ID或用户ID、时间范围
     * @return 权限变更历史详情
     */
    @PostMapping("/recent-changes")
    public PermResult<PermissionRecentChangesResp> recentChanges(@Valid @RequestBody PermissionRecentChangesReq req) {
        return PermResult.success(permissionViewService.recentChanges(TenantContextHolder.getTenantId(), req));
    }
}