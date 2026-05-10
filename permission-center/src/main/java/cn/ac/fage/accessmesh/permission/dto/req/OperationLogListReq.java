package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 操作日志列表查询请求体
 * <p>
 * 用于查询操作日志列表，支持按模块和操作过滤，支持标准分页。
 * </p>
 *
 * @param module   模块，可选，用于过滤
 * @param action   操作，可选，用于过滤
 * @param pageNum  页码，必填
 * @param pageSize 每页条数，必填
 */
public record OperationLogListReq(
    String module,
    String action,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}