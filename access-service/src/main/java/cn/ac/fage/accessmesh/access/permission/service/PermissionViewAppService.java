package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;

import java.util.Set;

/**
 * 权限视图应用服务接口
 * <p>
 * 原权限排查视图方法族（effective-permissions/resource-users/role-permissions/
 * explain/effective-roles/resource-tree）已删除（T-PERM-059，2026-09-10 删除重设计定案）；
 * 现仅承载登录权限串链路（effective-permission-codes + 菜单派生资源访问事实）。
 * </p>
 */
public interface PermissionViewAppService {

    /**
     * 获取用户有效权限码聚合（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 不分页、扁平 {@code resourceTypeCode:operationCode} 字符串集；可供前端 hasPerms、
     * 功能开关、客户端能力下发等场景使用。
     * <ul>
     *   <li>本接口不分页 —— 大权限用户的所有有效权限码均会被返回，杜绝截断风险</li>
     *   <li>不携带来源角色、scopeAll、resource 实例等管理面字段</li>
     *   <li>必须在 req.resourceTypeCodes 显式声明白名单，避免下发无关资源类型</li>
     * </ul>
     *
     * @param tenantId 租户ID
     * @param req      有效权限码聚合请求
     * @return 有效权限码响应（perm 串列表）
     */
    UserEffectivePermissionCodesResp getEffectivePermissionCodes(Long tenantId, UserEffectivePermissionCodesReq req);

    /**
     * 获取用户有效权限码（permission 域独立 HTTP 入口，带实例级门禁）。
     * <p>
     * 与 {@link #getEffectivePermissionCodes} 的数据逻辑相同，但本方法承担「管理面/外部入口」门禁：
     * 自查（操作者 = 被查用户）豁免；查他人时操作者需对被查用户有 {@code USER:VIEW}，
     * 防任意登录用户枚举 ID 越权读取他人权限码。query 包内部调用应使用
     * {@link #getEffectivePermissionCodes}（其 HTTP 入口统一走 {@link #getEffectivePermissionCodesForManage} 门禁）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      有效权限码聚合请求
     * @return 有效权限码响应（perm 串列表）
     * @throws SecurityException 查他人且无 {@code USER:VIEW} 时抛出
     */
    UserEffectivePermissionCodesResp getEffectivePermissionCodesForManage(Long tenantId, UserEffectivePermissionCodesReq req);

    /**
     * 获取用户在指定资源类型上的有效资源实例访问事实（T-ACCESS-006 菜单派生公式用）。
     * <p>
     * 与 {@link #getEffectivePermissionCodes} 共享同一 forUserView 管线（相同门禁与过滤），
     * 但返回资源实例粒度：用户在哪些资源类型上有全范围（scopeAll）授权、以及有任意有效
     * 操作码的资源实例 ID 集合。调用方（如菜单可见性派生）据此判定「用户对该资源有任意 op」。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      有效权限查询请求（resourceTypeCodes 白名单为资源类型码）
     * @return 有效资源访问事实（allScopeTypes=全范围资源类型值集合；resourceEntityIds=有任意 op 的资源实例 ID 集合）
     */
    EffectiveResourceAccess getEffectiveResourceAccess(Long tenantId, UserEffectivePermissionCodesReq req);

    /**
     * 有效资源访问事实（resource 实例粒度）。
     *
     * @param allScopeTypes     用户有 scopeAll（全范围）授权的资源类型值集合
     * @param resourceEntityIds 用户有任意有效操作码的资源实例 ID 集合（非 null）
     */
    record EffectiveResourceAccess(Set<Integer> allScopeTypes, Set<Long> resourceEntityIds) {}
}
