package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 权限最近变更列表响应体
 * <p>
 * 返回权限最近变更的分页列表。
 * 用于权限变更历史查询接口的响应。
 * </p>
 *
 * @param items    变更条目列表
 * @param total    总记录数
 * @param pageNum  当前页码
 * @param pageSize 每页条数
 * @param hasNext  是否有下一页
 */
public record PermissionRecentChangesResp(
    List<RecentChangeResp> items,
    int total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {}