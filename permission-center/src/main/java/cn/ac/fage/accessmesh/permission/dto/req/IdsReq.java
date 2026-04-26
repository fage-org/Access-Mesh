package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IdsReq(
    @NotNull Long tenantId,
    @NotEmpty List<Long> ids
) {}
