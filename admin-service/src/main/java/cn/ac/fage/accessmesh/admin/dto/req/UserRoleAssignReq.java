package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 用户角色分配请求（admin 代理 permission-center）。
 * <p>
 * 前端传入业务键（userId + roleTypeCode + roleExternalId），
 * 代理层直接透传给 permission-center，无需 ID → 业务键翻译。
 * 门禁：ADMIN_ROLE:GRANT（对目标角色做实例级校验）。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.2
 *
 * @param userId         用户 ID（必填）
 * @param roleTypeCode   角色类型码（必填，如 BASIC_ROLE / GROUP_ROLE / PERSONAL）
 * @param roleExternalId 角色外部标识（必填，permission-center 业务键）
 * @param validFrom      有效期起始（可选）
 * @param validTo        有效期截止（可选）
 */
public record UserRoleAssignReq(
    @NotNull Long userId,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    LocalDateTime validFrom,
    LocalDateTime validTo
) {}
