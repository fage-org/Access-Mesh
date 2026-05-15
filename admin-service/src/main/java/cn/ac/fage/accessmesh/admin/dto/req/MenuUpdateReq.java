package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 菜单更新请求记录类
 * <p>
 * 用于更新菜单信息的请求参数。
 * 所有字段均为可选，仅更新提供的字段。
 * </p>
 *
 * @param id        菜单ID（必填，用于定位菜单）
 * @param menuType  菜单类型（可选，0=目录，1=菜单，2=按钮）
 * @param menuName  菜单名称（可选）
 * @param parentId  父级菜单ID（可选）
 * @param path      路由路径（可选）
 * @param component 组件路径（可选）
 * @param perms     权限标识（可选）
 * @param icon      图标名称（可选）
 * @param sort      排序号（可选）
 * @param visible   是否可见（可选）
 * @param status    状态（可选）
 */
public record MenuUpdateReq(
    /**
     * 菜单ID
     */
    @NotNull(message = "菜单ID不能为空")
    Long id,

    /**
     * 菜单类型（0=目录，1=菜单，2=按钮）
     */
    Integer menuType,

    /**
     * 菜单名称
     */
    String menuName,

    /**
     * 父级菜单ID
     */
    Long parentId,

    /**
     * 路由路径
     */
    String path,

    /**
     * 组件路径
     */
    String component,

    /**
     * 权限标识
     */
    String perms,

    /**
     * 图标名称
     */
    String icon,

    /**
     * 排序号
     */
    Integer sort,

    /**
     * 是否可见（1=可见，0=隐藏）
     */
    Integer visible,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status
) {}