package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 用户角色列表查询请求。
 * <p>
 * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.1
 *
 * @param userId 用户 ID（必填）
 */
public record UserRoleListReq(
    @NotNull Long userId
) {}
