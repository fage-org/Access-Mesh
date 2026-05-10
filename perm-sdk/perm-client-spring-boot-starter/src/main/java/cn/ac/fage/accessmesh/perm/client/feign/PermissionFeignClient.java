package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.req.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 权限中心Feign客户端接口
 * <p>
 * 用于从其他服务调用权限中心的远程接口。
 * 所有接口路径遵循 {@code /api/perm/*} 契约，使用 POST + JSON Body 方式调用。
 * </p>
 */
@FeignClient(name = "permission-center")
public interface PermissionFeignClient {

    // ========== 用户同步 ==========

    /**
     * 同步用户到权限中心
     *
     * @param req 用户同步请求
     * @return 同步结果
     */
    @PostMapping("/api/perm/abstract-user/sync")
    PermResult<Map<String, Object>> syncUser(@RequestBody UserSyncReq req);

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
     * 权限校验
     *
     * @param req 权限校验请求
     * @return 权限校验结果
     */
    @PostMapping("/api/perm/auth/check")
    PermResult<PermCheckResp> checkPermission(@RequestBody PermCheckReq req);

    /**
     * 创建角色
     *
     * @param req 角色创建请求
     * @return 创建结果
     */
    @PostMapping("/api/perm/abstract-role/create")
    PermResult<Map<String, Object>> createRole(@RequestBody RoleCreateReq req);

    /**
     * 查询用户角色列表
     *
     * @param req 用户角色列表查询请求
     * @return 用户角色列表响应
     */
    @PostMapping("/api/perm/user-role/list")
    PermResult<UserRolesResp> getUserRoles(@RequestBody UserRoleListReq req);

    // ========== 资源同步 ==========

    /**
     * 创建资源
     *
     * @param req 资源创建请求
     * @return 创建结果
     */
    @PostMapping("/api/perm/resource-entity/create")
    PermResult<Map<String, Object>> createResource(@RequestBody ResourceCreateReq req);

    /**
     * 批量创建资源
     *
     * @param req 资源批量创建请求
     * @return 创建结果
     */
    @PostMapping("/api/perm/resource-entity/batch-create")
    PermResult<Map<String, Object>> batchCreateResources(@RequestBody ResourceBatchCreateReq req);

    /**
     * 更新资源
     *
     * @param req 资源更新请求
     * @return 更新结果
     */
    @PostMapping("/api/perm/resource-entity/update")
    PermResult<Map<String, Object>> updateResource(@RequestBody ResourceUpdateReq req);

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
    PermResult<Map<String, Object>> listOperations(@RequestBody OperationListReq req);

    // ========== 权限授予/撤销 ==========

    /**
     * 批量授予权限
     *
     * @param req 角色权限授予请求
     * @return 授予结果
     */
    @PostMapping("/api/perm/role-resource-permission/save")
    PermResult<Map<String, Object>> batchGrant(@RequestBody RoleGrantReq req);

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
    PermResult<PermissionEffectivePermissionsResp<Map<String, Object>>> getEffectivePermissions(
        @RequestBody UserPermissionViewReq req);
}