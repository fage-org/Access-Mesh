package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 变更日志列表查询请求体
 * <p>
 * 用于查询权限变更日志列表，支持按实体类型和ID过滤，支持标准分页。
 * </p>
 *
 * @param entityType 实体类型，可选，用于过滤
 * @param entityId   实体ID，可选，用于过滤
 * @param pageNum    页码，必填
 * @param pageSize   每页条数，必填
 */
public record ChangeLogListReq(
    String entityType,
    Long entityId,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}