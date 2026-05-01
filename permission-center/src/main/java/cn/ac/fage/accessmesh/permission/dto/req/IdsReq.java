package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Batch ids request body for remove APIs.
 */
public record IdsReq(
    @NotEmpty List<Long> ids
) {}
