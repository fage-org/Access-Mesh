package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 单ID请求
 * <p>
 * 用于需要租户ID和单个ID的获取/删除接口。
 * 租户ID通过请求头传递。
 * </p>
 */
public record IdWithTenantReq(
    /**
     * 主键ID
     */
    @NotNull Long id
) {}
