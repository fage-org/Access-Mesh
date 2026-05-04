package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record IdsReq(
    @NotEmpty(message = "ID列表不能为空")
    List<Long> ids
) {}