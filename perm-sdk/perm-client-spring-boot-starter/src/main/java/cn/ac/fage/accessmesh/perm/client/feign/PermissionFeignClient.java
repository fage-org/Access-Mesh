package cn.ac.fage.accessmesh.perm.client.feign;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.req.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
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
    R<Void> deleteUsers(@RequestBody IdsReq req);

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
    R<AuthCheckResp> checkAuth(@RequestBody AuthCheckReq req);

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
    R<BatchAuthCheckResp> batchCheckAuth(@RequestBody BatchAuthCheckReq req);

    /**
     * 查询主体可访问资源集合（T-API-002 补齐，core-flows §15 SDK 四件套之一）
     * <p>
     * 返回指定资源类型和操作下用户可访问/可管理的资源业务键集合
     * （scopeMode=INSTANCE 列实例，ALL 表示全量范围）。
     * </p>
     *
     * @param req 资源查询请求
     * @return 资源条目列表与缓存有效期
     */
    @PostMapping("/api/perm/auth/query-resources")
    R<QueryResourcesResp> queryResources(@RequestBody QueryResourcesReq req);

    /**
     * 查询主资源上下文内的范围权限四态（T-API-002 补齐，core-flows §15 SDK 四件套之一）
     * <p>
     * 按 {@code (resourceTypeCode, operationCode)} 分格返回 DENIED/INSTANCE/ALL/EMPTY，
     * 数据权限运行时消费端（DIRECT ∪ DEPENDENT 并集）。
     * </p>
     *
     * @param req 范围查询请求
     * @return 范围分组与缓存有效期
     */
    @PostMapping("/api/perm/auth/query-scopes")
    R<QueryScopesResp> queryScopes(@RequestBody QueryScopesReq req);

    // ========== 角色管理 ==========

    /**
     * 创建角色
     *
     * @param req 角色创建请求
     * @return 创建结果，包含角色详细信息
     */
    @PostMapping("/api/perm/abstract-role/create")
    R<RoleResp> createRole(@RequestBody RoleCreateReq req);

    /**
     * 查询角色列表
     *
     * @param req 角色列表查询请求，支持按角色类型过滤
     * @return 角色列表
     */
    @PostMapping("/api/perm/abstract-role/list")
    R<PageResp<RoleResp>> listRoles(@RequestBody RoleListReq req);

    @PostMapping("/api/perm/abstract-role/detail")
    R<RoleResp> getRole(@RequestBody IdReq req);

    /**
     * 查询用户角色列表
     *
     * @param req 用户角色列表查询请求
     * @return 用户角色列表响应
     */
    @PostMapping("/api/perm/user-role/list")
    R<UserRolesResp> getUserRoles(@RequestBody UserRoleListReq req);

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
    R<Void> assignRole(@RequestBody UserAssignRoleReq req);

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
    R<Void> revokeRoles(@RequestBody UserRoleBatchRevokeReq req);

    // ========== 赋源同步 ==========

    /**
     * 创建资源
     *
     * @param req 资源创建请求
     * @return 创建结果，包含资源详细信息
     */
    @PostMapping("/api/perm/resource-entity/create")
    R<ResourceResp> createResource(@RequestBody ResourceCreateReq req);

    /**
     * 批量创建资源
     *
     * @param req 资源批量创建请求
     * @return 创建结果，包含资源列表
     */
    @PostMapping("/api/perm/resource-entity/batch-create")
    R<ItemsResp<ResourceResp>> batchCreateResources(@RequestBody ResourceBatchCreateReq req);

    /**
     * 更新资源
     *
     * @param req 资源更新请求
     * @return 更新结果，包含资源详细信息
     */
    @PostMapping("/api/perm/resource-entity/update")
    R<ResourceResp> updateResource(@RequestBody ResourceUpdateReq req);

    /**
     * 批量删除资源
     *
     * @param req 资源ID列表请求
     * @return 删除结果
     */
    @PostMapping("/api/perm/resource-entity/remove")
    R<Void> deleteResources(@RequestBody IdsReq req);

    // ========== 操作权限 ==========

    /**
     * 查询操作权限列表
     *
     * @param req 操作列表查询请求
     * @return 操作权限列表
     */
    @PostMapping("/api/perm/operation-permission/list")
    R<ItemsResp<OperationPermissionResp>> listOperations(@RequestBody OperationListReq req);

    // ========== 权限授予/撤销 ==========
    // 旧写入口 batchGrant(/save)/batchRevoke(/revoke) 已随 T-PERM-034 端点退役删除（2026-08-27）；
    // 授权写入唯一入口为 /api/perm/role-resource-permission/apply-grant-plan（记录级 plan 单事务原子）。

    /**
     * 查询用户有效权限码聚合（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 不分页、扁平 {@code resourceTypeCode:operationCode} 字符串列表，可供前端 hasPerms、
     * 功能开关、客户端能力下发等场景使用。
     * 不分页聚合，杜绝大权限用户被分页截断的故障模式。
     *
     * @param req 有效权限码聚合请求（含 resourceTypeCodes 白名单）
     * @return perm 串列表
     */
    @PostMapping("/api/perm/permission-view/effective-permission-codes")
    R<UserEffectivePermissionCodesResp> getEffectivePermissionCodes(
        @RequestBody UserEffectivePermissionCodesReq req);
}
