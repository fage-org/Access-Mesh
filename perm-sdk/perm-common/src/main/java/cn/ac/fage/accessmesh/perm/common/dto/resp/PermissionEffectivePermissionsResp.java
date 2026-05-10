package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 有效权限分页响应
 * <p>
 * 用于返回用户有效权限的分页查询结果。
 * </p>
 *
 * @param <T> 权限项类型
 */
public record PermissionEffectivePermissionsResp<T>(
    /**
     * 目标类型
     */
    String targetType,
    /**
     * 权限项列表
     */
    List<T> items,
    /**
     * 总数量
     */
    int total,
    /**
     * 当前页码
     */
    int pageNum,
    /**
     * 每页大小
     */
    int pageSize,
    /**
     * 是否有下一页
     */
    boolean hasNext
) {}
