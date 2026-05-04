package cn.ac.fage.accessmesh.common.model;

import jakarta.validation.constraints.NotNull;

/**
 * 通用ID请求DTO
 */
public record IdReq(
    @NotNull Long id
) {
    public static IdReq of(Long id) {
        return new IdReq(id);
    }
}