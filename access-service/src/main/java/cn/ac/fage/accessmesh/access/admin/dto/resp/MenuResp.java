package cn.ac.fage.accessmesh.access.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单响应记录类（v3.5 菜单零权限化终态，T-ACCESS-015）
 * <p>
 * 对齐权威 DDL sys_menu 列；不含权限字段
 * （perm_code/component/visible 已随 v3.5 移除）。
 * </p>
 *
 * @param id            菜单ID
 * @param menuType      菜单类型（DIR/MENU/EXTERNAL/IFRAME/HIDDEN）
 * @param displayName   菜单显示名
 * @param parentId      父级菜单ID（顶级为 0）
 * @param path          路由路径（EXTERNAL/IFRAME 为外链 URL）
 * @param icon          图标名称
 * @param sortOrder     排序权重
 * @param status        状态（1=ENABLED，0=DISABLED）
 * @param resourceType  关联业务资源类型（仅 link，不参与鉴权决策）
 * @param resourceCode  关联业务资源实例（仅 link，不参与鉴权决策）
 * @param sourceService 业务服务标识（链路追溯）
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @param children      子菜单列表（树形结构）
 */
public record MenuResp(
    /**
     * 菜单ID
     */
    Long id,

    /**
     * 菜单类型（DIR=目录，MENU=菜单，EXTERNAL=外链，IFRAME=嵌入，HIDDEN=隐藏路由）
     */
    String menuType,

    /**
     * 菜单显示名
     */
    String displayName,

    /**
     * 父级菜单ID（顶级为 0）
     */
    Long parentId,

    /**
     * 路由路径（EXTERNAL/IFRAME 为外链 URL）
     */
    String path,

    /**
     * 图标名称
     */
    String icon,

    /**
     * 排序权重（升序）
     */
    Integer sortOrder,

    /**
     * 状态（1=ENABLED，0=DISABLED）
     */
    Integer status,

    /**
     * 关联业务资源类型（仅 link）
     */
    String resourceType,

    /**
     * 关联业务资源实例（仅 link）
     */
    String resourceCode,

    /**
     * 业务服务标识（链路追溯）
     */
    String sourceService,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt,

    /**
     * 子菜单列表（树形结构）
     */
    List<MenuResp> children
) {}
