package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceDetailReq(
    @Size(max = 64) String domainCode,
    @NotBlank(message = "资源类型编码不能为空") @Size(max = 64) String resourceTypeCode,
    @NotBlank(message = "资源编码不能为空") @Size(max = 256) String resourceCode,
    @Size(max = 32) String codeType
) {}