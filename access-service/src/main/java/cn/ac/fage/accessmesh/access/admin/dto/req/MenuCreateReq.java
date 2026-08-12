package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单创建请求记录类
 * <p>
 * 用于创建新菜单的请求参数。
 * 包含菜单类型、名称、父级菜单、路由配置、权限标识等。
 * </p>
 *
 * @param menuType  菜单类型（必填，0=目录，1=菜单，2=按钮）
 * @param menuName  菜单名称（必填）
 * @param parentId  级菜单ID（可选，null表示顶级菜单）
 * @param path      路由路径（可选）
 * @param component 组件路径（可选）
 * @param perms     权限标识（可选）
 * @param icon      图标名称（可选）
 * @param sort      排序号（可选）
 * @param visible   是否可见（可选，默认1=可见）
 * @param status    状态（可选，默认0=正常）
 */
public record MenuCreateReq(
    /**
     * 菜单类型（0=目录，1=菜单，2=按钮）
     */
    @NotNull(message = "菜单类型不能为空")
    Integer menuType,

    /**
     * 菜单名称
     */
    @NotBlank(message = "菜单名称不能为空")
    String menuName,

    /**
     * 父级菜单ID（null表示顶级菜单）
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
     * 权限标识（如：ADMIN_USER:CREATE）
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