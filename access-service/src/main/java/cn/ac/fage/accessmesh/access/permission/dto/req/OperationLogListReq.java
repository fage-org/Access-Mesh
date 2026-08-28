package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 操作日志列表查询请求体
 * <p>
 * 用于查询操作日志列表，支持按模块、操作、操作者、时间范围、目标类型过滤，支持标准分页（T-PERM-025 扩展筛选维度）。
 * </p>
 *
 * @param module     模块，可选，用于过滤（ADMIN/PERMISSION/ACCESS）
 * @param action     操作，可选，精确匹配（动态字典见 /log/operation/action-options）
 * @param operatorId 操作者用户ID，可选
 * @param since      创建时间下界（含），可选（ISO 无偏移 UTC 墙钟）
 * @param until      创建时间上界（含），可选（同上）
 * @param targetType 目标类型，可选，精确匹配
 * @param pageNum    页码，必填
 * @param pageSize   每页条数，必填
 */
public record OperationLogListReq(
    String module,
    String action,
    Long operatorId,
    LocalDateTime since,
    LocalDateTime until,
    String targetType,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}
