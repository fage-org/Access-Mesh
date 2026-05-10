package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 角色移动请求体
 * <p>
 * 用于移动角色到新的父角色下，调整层级结构。
 * </p>
 *
 * @param roleId  角色ID，必填
 * @param parentId 新父角色ID，可选，null表示移动到根级别
 */
public record RoleMoveReq(
    @NotNull Long roleId,
    Long parentId
) {}