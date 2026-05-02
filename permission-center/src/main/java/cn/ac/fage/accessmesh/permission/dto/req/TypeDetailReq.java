package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TypeDetailReq(
    @NotBlank(message = "类型键不能为空") @Size(max = 64) String typeKey
) {}