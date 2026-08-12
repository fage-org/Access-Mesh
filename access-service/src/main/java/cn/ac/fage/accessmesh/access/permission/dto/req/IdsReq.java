package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * ID集合请求体
 * <p>
 * 用于批量操作的API接口，如批量删除、批量查询等。
 * 包含一个ID列表，至少需要一个ID。
 * </p>
 *
 * @param ids ID列表，必填且不能为空
 */
public record IdsReq(
    @NotEmpty List<Long> ids
) {}