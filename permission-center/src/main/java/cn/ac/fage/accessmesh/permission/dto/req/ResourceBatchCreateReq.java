package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 资源批量创建请求体
 * <p>
 * 用于批量创建多个资源实体。
 * </p>
 *
 * @param items 资源创建请求列表，必填且不能为空
 */
public record ResourceBatchCreateReq(
    @NotEmpty List<ResourceCreateReq> items
) {}