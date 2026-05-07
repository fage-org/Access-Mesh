package cn.ac.fage.accessmesh.admin.dto.auth;

import java.util.List;

/**
 * 用户菜单响应
 * 包含动态路由、角色和按钮级权限
 */
public record UserMenuResp(
    /** 动态路由树 */
    List<MenuRouteItem> menus,
    /** 用户角色列表 */
    List<String> roles,
    /** 按钮级权限列表 */
    List<String> permissions
) {
    /**
     * 前端路由项
     */
    public record MenuRouteItem(
        /** 路由路径 */
        String path,
        /** 路由名称 */
        String name,
        /** 组件路径（相对于 src/views） */
        String component,
        /** 重定向路径 */
        String redirect,
        /** 路由元信息 */
        MetaInfo meta,
        /** 子路由 */
        List<MenuRouteItem> children
    ) {}

    /**
     * 路由元信息
     */
    public record MetaInfo(
        /** 菜单标题 */
        String title,
        /** 菜单图标 */
        String icon,
        /** 菜单排序 */
        Integer rank,
        /** 是否显示菜单 */
        Boolean showLink,
        /** 是否缓存 */
        Boolean keepAlive,
        /** 外链地址 */
        String frameSrc,
        /** 页面级角色权限 */
        List<String> roles,
        /** 按钮级权限码 */
        List<String> auths
    ) {}
}