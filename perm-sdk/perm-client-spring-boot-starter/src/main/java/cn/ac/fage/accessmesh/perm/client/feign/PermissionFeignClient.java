package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.req.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.RolePermissionItemsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 权限服务Feign客户端接口
 * <p>
 * 用于从其他服务调用access-service的远程接口（T-ACCESS-010：目标由 permission-center 切换为
 * access-service，服务发现名与部署单元统一，HTTP 契约不变）。
 * 所有接口路径遵循 {@code /api/perm/*} 契约，使用 POST + JSON Body 方式调用。
 * </p>
 */
@FeignClient(name = "access-service")
public interface PermissionFeignClient {

    // ========== 用户管理 ==========

    /**
     * 批量删除用户
     *
     * @param req 用户ID列表请求
     * @return 删除结果
     */
    @PostMapping("/api/perm/abstract-user/remove")
    PermResult<Void> deleteUsers(@RequestBody IdsReq req);

    // ========== 权限校验（新API，使用稳定业务键） ==========

    /**
     * 单条权限校验（使用稳定业务键）
     * <p>
     * 使用主体类型码、主体外部ID、资源类型码、资源码、操作码进行权限校验。
     * </p>
     *
     * @param req 权限校验请求
     * @return 权限校验结果，包含是否允许和拒绝原因
     */
    @PostMapping("/api/perm/auth/check")
    PermResult<AuthCheckResp> checkAuth(@RequestBody AuthCheckReq req);

    /**
     * 批量权限校验（使用稳定业务键）
     * <p>
     * 在单次调用中校验多个资源/操作的权限。
     * </p>
     *
     * @param req 批量权限校验请求
     * @return 批量校验结果，包含每个项目的权限状态
     */
    @PostMapping("/api/perm/auth/batch-check")
    PermResult<BatchAuthCheckResp> batchCheckAuth(@RequestBody BatchAuthCheckReq req);

    // ========== 角色管理 ==========

    /**
     * 创建角色
     *
     * @param req 角色创建请求
     * @return 创建结果，包含角色详细信息
     */
    @PostMapping("/api/perm/abstract-role/create")
    PermResult<RoleResp> createRole(@RequestBody RoleCreateReq req);

    /**
     * 查询角色列表
     *
     * @param req 角色列表查询请求，支持按角色类型过滤
     * @return 角色列表
     */
    @PostMapping("/api/perm/abstract-role/list")
    PermResult<PaginatedResp<RoleResp>> listRoles(@RequestBody RoleListReq req);

    @PostMapping("/api/perm/abstract-role/detail")
    PermResult<RoleResp> getRole(@RequestBody IdReq req);

    /**
     * 查询用户角色列表
     *
     * @param req 用户角色列表查询请求
     * @return 用户角色列表响应
     */
    @PostMapping("/api/perm/user-role/list")
    PermResult<UserRolesResp> getUserRoles(@RequestBody UserRoleListReq req);

    /**
     * 为用户分配角色
     * <p>
     * 将指定角色分配给用户，用户将获得该角色下的所有权限。
     * </p>
     *
     * @param req 用户角色分配请求
     * @return 操作成功结果
     */
    @PostMapping("/api/perm/user-role/assign")
    PermResult<Void> assignRole(@RequestBody UserAssignRoleReq req);

    /**
     * 批量撤销用户角色
     * <p>
     * 批量撤销用户的角色关联，用户将失去这些角色的权限。
     * </p>
     *
     * @param req 批量角色撤销请求
     * @return 操作成功结果
     */
    @PostMapping("/api/perm/user-role/revoke")
    PermResult<Void> revokeRoles(@RequestBody UserRoleBatchRevokeReq req);

    // ========== 赋源同步 ==========

    /**
     * 创建资源
     *
     * @param req 资源创建请求
     * @return 创建结果，包含资源详细信息
     */
    @PostMapping("/api/perm/resource-entity/create")
    PermResult<ResourceResp> createResource(@RequestBody ResourceCreateReq req);

    /**
     * 批量创建资源
     *
     * @param req 资源批量创建请求
     * @return 创建结果，包含资源列表
     */
    @PostMapping("/api/perm/resource-entity/batch-create")
    PermResult<ItemsResp<ResourceResp>> batchCreateResources(@RequestBody ResourceBatchCreateReq req);

    /**
     * 更新资源
     *
     * @param req 资源更新请求
     * @return 更新结果，包含资源详细信息
     */
    @PostMapping("/api/perm/resource-entity/update")
    PermResult<ResourceResp> updateResource(@RequestBody ResourceUpdateReq req);

    /**
     * 批量删除资源
     *
     * @param req 资源ID列表请求
     * @return 删除结果
     */
    @PostMapping("/api/perm/resource-entity/remove")
    PermResult<Void> deleteResources(@RequestBody IdsReq req);

    // ========== 操作权限 ==========

    /**
     * 查询操作权限列表
     *
     * @param req 操作列表查询请求
     * @return 操作权限列表
     */
    @PostMapping("/api/perm/operation-permission/list")
    PermResult<ItemsResp<OperationPermissionResp>> listOperations(@RequestBody OperationListReq req);

    // ========== 权限授予/撤销 ==========

    /**
     * 批量授予权限
     *
     * @param req 角色权限授予请求
     * @return 授予结果，包含权限条目列表
     */
    @PostMapping("/api/perm/role-resource-permission/save")
    PermResult<RolePermissionItemsResp> batchGrant(@RequestBody RoleGrantReq req);

    /**
     * 批量撤销权限
     *
     * @param req 批量撤销权限请求
     * @return 撤销结果
     */
    @PostMapping("/api/perm/role-resource-permission/revoke")
    PermResult<Void> batchRevoke(@RequestBody BatchRevokeReq req);

    /**
     * 查询用户有效权限视图
     *
     * @param req 用户权限视图查询请求
     * @return 有效权限分页结果
     */
    @PostMapping("/api/perm/permission-view/effective-permissions")
    PermResult<PermissionEffectivePermissionsResp> getEffectivePermissions(
        @RequestBody UserPermissionViewReq req);

    /**
     * 查询用户有效权限码聚合（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 不分页、扁平 {@code resourceTypeCode:operationCode} 字符串列表，可供前端 hasPerms、
     * 功能开关、客户端能力下发等场景使用。
     * 与 {@link #getEffectivePermissions} 解耦，杜绝大权限用户被分页截断的故障模式。
     *
     * @param req 有效权限码聚合请求（含 resourceTypeCodes 白名单）
     * @return perm 串列表
     */
    @PostMapping("/api/perm/permission-view/effective-permission-codes")
    PermResult<UserEffectivePermissionCodesResp> getEffectivePermissionCodes(
        @RequestBody UserEffectivePermissionCodesReq req);
}
