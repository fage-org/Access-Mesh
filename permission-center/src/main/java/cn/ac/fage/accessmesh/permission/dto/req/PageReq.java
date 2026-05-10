package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 分页请求体
 * <p>
 * 用于需要分页查询的API接口。
 * 包含页码和每页条数两个参数。
 * </p>
 *
 * @param pageNum  页码，必填，从1开始
 * @param pageSize 每页条数，必填
 */
public record PageReq(
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}