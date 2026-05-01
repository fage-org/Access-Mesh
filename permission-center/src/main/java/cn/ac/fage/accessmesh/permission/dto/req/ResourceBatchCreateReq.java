package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ResourceBatchCreateReq(
    @NotEmpty List<ResourceCreateReq> items
) {}
