package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Single ID request.
 *
 * @param id entity ID
 */
public record IdReq(
    @NotNull Long id
) {
}
