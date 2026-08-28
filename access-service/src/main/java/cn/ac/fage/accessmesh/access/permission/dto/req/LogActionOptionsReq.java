package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 操作日志 action 字典查询请求体
 * <p>
 * 返回 operation_log 当前实际存在的 action 去重集合（按 module 可选过滤），
 * 供前端筛选下拉动态拉取（T-PERM-025）。
 * </p>
 *
 * @param module 模块，可选，用于过滤（ADMIN/PERMISSION/ACCESS）
 */
public record LogActionOptionsReq(
    String module
) {}
