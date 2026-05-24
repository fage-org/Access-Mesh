package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.permission.service.UserManageAppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色关系管理控制器
 * <p>
 * 提供用户与角色关联的分配、撤销、查询等功能。
 * 用户通过获得角色来继承角色配置的权限。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/user-role")
public class UserRoleController {

    private final UserManageAppService userManageService;

    /**
     * 构造函数注入依赖
     *
     * @param userManageService 用户管理服务，包含角色分配功能
     */
    public UserRoleController(UserManageAppService userManageService) {
        this.userManageService = userManageService;
    }

    /**
     * 为用户分配单个角色
     * <p>
     * 将指定角色分配给用户，用户将获得该角色下的所有权限。
     * </p>
     *
     * @param req 用户角色分配请求，包含用户ID和角色ID
     * @return 操作成功结果
     */
    @PostMapping("/assign")
    public PermResult<Void> assignRole(@Valid @RequestBody UserAssignRoleReq req) {
        userManageService.assignRole(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    /**
     * 批量为用户分配角色
     * <p>
     * 批量将多个角色分配给多个用户。
     * </p>
     *
     * @param req 批量角色分配请求，包含用户ID列表和角色ID列表
     * @return 操作成功结果
     */
    @PostMapping("/batch-assign")
    public PermResult<Void> batchAssignRole(@Valid @RequestBody UserRoleBatchAssignReq req) {
        userManageService.assignRolesBatch(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    /**
     * 批量撤销用户角色
     * <p>
     * 批量撤销用户的角色关联，用户将失去这些角色的权限。
     * </p>
     *
     * @param req 批量角色撤销请求，包含用户角色关联ID列表
     * @return 操作成功结果
     */
    @PostMapping("/revoke")
    public PermResult<Void> revokeRoles(@Valid @RequestBody UserRoleBatchRevokeReq req) {
        userManageService.revokeRolesBatch(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    /**
     * 查询用户的角色列表
     * <p>
     * 查询用户当前拥有的所有角色，包括直接分配和组角色继承的角色。
     * </p>
     *
     * @param req 用户角色列表查询请求，包含用户ID
     * @return 用户角色列表响应
     */
    @PostMapping("/list")
    public PermResult<UserRolesResp> getUserRoles(@Valid @RequestBody UserRoleListReq req) {
        return PermResult.success(userManageService.getUserRoles(TenantContextHolder.getTenantId(), req));
    }
}