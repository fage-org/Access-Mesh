package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 依赖列表查询请求体
 * <p>
 * 用于查询资源依赖关系列表，可选按资源实体ID过滤。
 * </p>
 *
 * @param resourceEntityId 资源实体ID，可选，用于过滤
 */
public record DependencyListReq(
    Long resourceEntityId
) {}