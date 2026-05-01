package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record RoleCreateReq(
    Long bizDomainId,
    Long parentId,
    @NotBlank(message = "角色类型不能为空")
    String roleTypeCode,
    String externalId,
    @NotBlank(message = "角色名称不能为空")
    String name,
    Integer sortOrder,
    String extra
) {}
