package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户角色回收请求（admin 代理 access-service）。
 * <p>
 * 前端传入业务键（userId + roleTypeCode + roleExternalId），
 * 代理层直接透传给 access-service，无需 ID → 业务键翻译。
 * 门禁：ROLE:MANAGE（接口已随 T-ACCESS-006 退役恒抛 10111，现行链路 /api/perm/user-role/revoke，permission 域 enforce）。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.3
 *
 * @param userId         用户 ID（必填）
 * @param roleTypeCode   角色类型码（必填）
 * @param roleExternalId 角色外部标识（必填，access-service 业务键）
 */
public record UserRoleRevokeReq(
    @NotNull Long userId,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId
) {}
