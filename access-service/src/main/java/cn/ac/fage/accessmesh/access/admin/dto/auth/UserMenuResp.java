package cn.ac.fage.accessmesh.access.admin.dto.auth;

import java.util.List;

/**
 * 用户菜单响应
 * <p>
 * 包含前端动态路由配置、用户角色列表和按钮级权限标识。
 * 用于前端初始化路由、权限控制和界面渲染。
 * </p>
 */
public record UserMenuResp(
    /**
     * 动态路由树
     * <p>
     * 前端动态路由配置列表，用于构建侧边栏菜单和路由表。
     * </p>
     */
    List<MenuRouteItem> menus,
    /**
     * 用户角色列表
     * <p>
     * 用户拥有的角色编码列表，用于角色级别的权限控制。
     * </p>
     */
    List<String> roles,
    /**
     * 按钮级权限列表
     * <p>
     * 用户拥有的按钮级权限标识列表，用于界面按钮的显示控制。
     * </p>
     */
    List<String> permissions
) {
    /**
     * 前端路由项
     * <p>
     * 定义单个路由的完整配置信息，包括路径、组件、元信息等。
     * 支持嵌套子路由，构建多级菜单结构。
     * </p>
     */
    public record MenuRouteItem(
        /**
         * 路由路径
         * <p>
         * URL路径，如 "/system/user"。
         * </p>
         */
        String path,
        /**
         * 路由名称
         * <p>
         * 路由的唯一名称标识，用于路由跳转。
         * </p>
         */
        String name,
        /**
         * 组件路径
         * <p>
         * Vue组件相对路径（相对于 src/views），如 "system/user/index"。
         * </p>
         */
        String component,
        /**
         * 重定向路径
         * <p>
         * 路由重定向目标路径，用于默认跳转。
         * </p>
         */
        String redirect,
        /**
         * 路由元信息
         * <p>
         * 包含菜单标题、图标、排序等附加配置。
         * </p>
         */
        MetaInfo meta,
        /**
         * 子路由
         * <p>
         * 嵌套的子路由列表，构建多级菜单结构。
         * </p>
         */
        List<MenuRouteItem> children
    ) {}

    /**
     * 路由元信息
     * <p>
     * 路由的附加配置信息，用于菜单渲染和权限控制。
     * 包括显示控制、缓存策略、权限标识等。
     * </p>
     */
    public record MetaInfo(
        /**
         * 菜单标题
         * <p>
         * 菜单显示名称，支持国际化。
         * </p>
         */
        String title,
        /**
         * 菜单图标
         * <p>
         * 菜单图标名称或图标类名。
         * </p>
         */
        String icon,
        /**
         * 菜单排序
         * <p>
         * 菜单排序权重，数值越小越靠前。
         * </p>
         */
        Integer rank,
        /**
         * 是否显示菜单
         * <p>
         * 控制菜单项是否在侧边栏显示，隐藏路由仍可访问。
         * </p>
         */
        Boolean showLink,
        /**
         * 是否缓存
         * <p>
         * 开启KeepAlive缓存，保持页面状态。
         * </p>
         */
        Boolean keepAlive,
        /**
         * 外链地址
         * <p>
         * 嵌入外部页面的URL地址，用于iframe嵌入。
         * </p>
         */
        String frameSrc,
        /**
         * 页面级角色权限
         * <p>
         * 访问该页面需要的角色列表，空表示无限制。
         * </p>
         */
        List<String> roles,
        /**
         * 按钮级权限码
         * <p>
         * 页面内按钮的权限标识列表，用于按钮级权限控制。
         * </p>
         */
        List<String> auths
    ) {}
}