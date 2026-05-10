package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 资源批量创建请求
 * <p>
 * 用于在权限中心批量创建资源的共享请求对象。
 * </p>
 */
public record ResourceBatchCreateReq(
    /**
     * 资源创建项列表
     */
    @NotEmpty List<ResourceCreateReq> items
) {}