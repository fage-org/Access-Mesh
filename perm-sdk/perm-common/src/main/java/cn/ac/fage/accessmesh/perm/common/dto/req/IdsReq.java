package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量ID请求
 * <p>
 * 用于批量删除等接口的主键ID列表请求。
 * 与权限中心的接口契约保持一致。
 * </p>
 */
public record IdsReq(
    /**
     * 主键ID列表
     */
    @NotEmpty List<Long> ids
) {}
