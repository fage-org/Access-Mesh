package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 用户角色回收请求（admin 代理 permission-center）。
 * <p>
 * 前端传入 admin-service 数字 ID（userId + roleId），
 * 代理层完成 ID → 业务键翻译后调用 permission-center。
 * 门禁：ROLE:MANAGE（对目标角色做实例级校验）。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.3
 *
 * @param userId 用户 ID（必填）
 * @param roleId 角色 ID（必填，permission-center abstract_role.id）
 */
public record UserRoleRevokeReq(
    @NotNull Long userId,
    @NotNull Long roleId
) {}
