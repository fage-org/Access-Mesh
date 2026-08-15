package cn.ac.fage.accessmesh.access.application.query.projection;

/**
 * 菜单投影（跨域只读查询用）。
 * <p>
 * 对应权威 schema（access-service.sql）sys_menu 的列，菜单树构建所需字段；
 * 不暴露领域实体。注意：schema 菜单列收敛后（display_name/DIR-MENU 枚举）已无
 * component/visible/perm_code 等旧列，服务层构建菜单树时对缺失字段取默认值
 * （见 UserMenuQueryServiceImpl 构建说明）。
 * </p>
 *
 * @param id           菜单 ID
 * @param parentId     父菜单 ID（根级为 null）
 * @param menuType     菜单类型（DIR/MENU/EXTERNAL/IFRAME/HIDDEN）
 * @param name         菜单显示名（display_name）
 * @param path         路由路径
 * @param icon         图标
 * @param sortOrder    排序权重
 * @param status       状态（0=DISABLED，1=ENABLED）
 * @param resourceType 关联业务资源类型（不参与鉴权决策，仅 link）
 * @param resourceCode 关联业务资源实例（不参与鉴权决策，仅 link）
 */
public record MenuProjection(
    Long id,
    Long parentId,
    String menuType,
    String name,
    String path,
    String icon,
    Integer sortOrder,
    Integer status,
    String resourceType,
    String resourceCode
) {}
