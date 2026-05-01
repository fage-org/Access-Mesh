package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Shared: create a role in permission-center.
 */
public record RoleCreateReq(
    Long bizDomainId,
    Long parentId,
    @NotBlank(message = "角色类型不能为空") String roleTypeCode,
    String externalId,
    @NotBlank(message = "角色名称不能为空") String name,
    Integer sortOrder,
    String extra
) {}
