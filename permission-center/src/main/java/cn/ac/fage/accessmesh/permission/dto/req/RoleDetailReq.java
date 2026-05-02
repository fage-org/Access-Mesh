package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleDetailReq(
    @Size(max = 64) String domainCode,
    @NotBlank(message = "角色类型编码不能为空") @Size(max = 64) String roleTypeCode,
    @NotBlank(message = "角色外部标识不能为空") @Size(max = 128) String roleExternalId
) {}