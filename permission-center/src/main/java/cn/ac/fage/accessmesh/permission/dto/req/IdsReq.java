package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record IdsReq(
    @NotEmpty List<Long> ids
) {}
