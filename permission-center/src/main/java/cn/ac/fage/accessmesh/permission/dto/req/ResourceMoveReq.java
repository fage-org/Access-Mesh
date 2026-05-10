package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 资源移动请求体
 * <p>
 * 用于移动资源到新的父资源下，调整层级结构。
 * </p>
 *
 * @param resourceId 资源ID，必填
 * @param parentId   新父资源ID，可选，null表示移动到根级别
 */
public record ResourceMoveReq(
    @NotNull Long resourceId,
    Long parentId
) {}