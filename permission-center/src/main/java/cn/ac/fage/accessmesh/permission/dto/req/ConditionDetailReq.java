package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConditionDetailReq(
    @NotBlank(message = "条件编码不能为空") @Size(max = 64) String conditionCode
) {}