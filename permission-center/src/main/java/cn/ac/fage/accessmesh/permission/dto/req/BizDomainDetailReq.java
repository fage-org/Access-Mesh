package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BizDomainDetailReq(
    @NotBlank(message = "业务域编码不能为空") @Size(max = 64) String domainCode
) {}