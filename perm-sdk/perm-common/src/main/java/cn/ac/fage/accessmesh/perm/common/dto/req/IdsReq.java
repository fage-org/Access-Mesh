package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Batch primary-key ids for remove APIs (aligns with permission-center contract).
 */
public record IdsReq(
    @NotEmpty List<Long> ids
) {}
