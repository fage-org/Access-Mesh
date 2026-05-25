package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 列表响应体
 * <p>
 * 统一的列表响应格式，用于返回多个对象的列表。
 * 例如：查询角色列表、查询资源列表等操作。
 * </p>
 *
 * @param items 对象列表
 * @param <T>   对象类型
 */
public record ItemsResp<T>(
    List<T> items
) {}