package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户角色列表查询请求
 * <p>
 * 用于查询用户关联的角色列表。
 * </p>
 */
public record UserRoleListReq(
    /**
     * 主体类型码
     */
    @NotBlank String subjectTypeCode,
    /**
     * 主体外部ID
     */
    @NotBlank String subjectExternalId
) {}
