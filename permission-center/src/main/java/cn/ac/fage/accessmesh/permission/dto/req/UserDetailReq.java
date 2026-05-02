package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserDetailReq(
    @NotBlank(message = "主体类型编码不能为空") @Size(max = 64) String subjectTypeCode,
    @NotBlank(message = "主体外部标识不能为空") @Size(max = 128) String subjectExternalId
) {}