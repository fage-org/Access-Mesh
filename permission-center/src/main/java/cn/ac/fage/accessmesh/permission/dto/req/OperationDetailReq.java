package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OperationDetailReq(
    @NotBlank(message = "资源类型编码不能为空") @Size(max = 64) String resourceTypeCode,
    @NotBlank(message = "操作编码不能为空") @Size(max = 64) String operationCode
) {}