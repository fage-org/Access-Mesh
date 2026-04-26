package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrgCreateReq(
    @NotNull(message = "组织类型不能为空")
    Integer orgType,
    @NotBlank(message = "组织名称不能为空")
    String orgName,
    String parentOrgId,
    String code,
    String phone,
    String email,
    Integer status,
    Integer sort
) {}
