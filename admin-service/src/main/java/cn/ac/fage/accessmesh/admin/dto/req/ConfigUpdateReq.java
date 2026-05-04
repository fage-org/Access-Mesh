package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;

public record ConfigUpdateReq(
    @NotNull(message = "配置ID不能为空")
    Long id,
    String configValue,
    String remark
) {}
