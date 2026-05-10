package cn.ac.fage.accessmesh.common.model;

import jakarta.validation.constraints.NotNull;

/**
 * 通用ID请求DTO
 * <p>
 * 用于需要单个ID作为参数的请求。
 * </p>
 */
public record IdReq(
    /**
     * 实体ID（必填）
     */
    @NotNull Long id
) {
    /**
     * 创建ID请求实例
     *
     * @param id 实体ID
     * @return ID请求实例
     */
    public static IdReq of(Long id) {
        return new IdReq(id);
    }
}