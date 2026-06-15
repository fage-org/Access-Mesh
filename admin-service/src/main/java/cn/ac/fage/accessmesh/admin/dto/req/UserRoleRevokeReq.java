package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 用户角色回收请求（admin 代理 permission-center）。
 * <p>
 * 前端传入业务键（userId + roleTypeCode + roleExternalId），
 * 代理层直接透传给 permission-center，无需 ID → 业务键翻译。
 * 门禁：ADMIN_ROLE:REVOKE（对目标角色做实例级校验）。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.3
 *
 * @param userId         用户 ID（必填）
 * @param roleTypeCode   角色类型码（必填）
 * @param roleExternalId 角色外部标识（必填，permission-center 业务键）
 */
public record UserRoleRevokeReq(
    @NotNull Long userId,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId
) {}
