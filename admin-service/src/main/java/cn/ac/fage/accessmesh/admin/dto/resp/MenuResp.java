package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单响应记录类
 * <p>
 * 用于返回菜单信息，支持树形结构。
 * 包含菜单类型、名称、父级、路由配置、权限标识、图标、排序、状态、子菜单列表。
 * </p>
 *
 * @param id         菜单ID
 * @param menuType   菜单类型（0=目录，1=菜单，2=按钮）
 * @param menuName   菜单名称
 * @param parentId   父级菜单ID
 * @param path       路由路径
 * @param component  组件路径
 * @param perms      权限标识
 * @param icon       图标名称
 * @param sort       排序号
 * @param visible    是否可见（1=可见，0=隐藏）
 * @param status     状态（0=正常，1=禁用）
 * @param createdAt  创建时间
 * @param updatedAt  更新时间
 * @param children   子菜单列表（树形结构）
 */
public record MenuResp(
    /**
     * 菜单ID
     */
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
    Integer status,

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