package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;

/**
 * 菜单同步处理器
 * 将 admin-service 的菜单同步到 permission-center 的 resource_entity
 */
public interface MenuSyncHandler {

    /**
     * 同步菜单到权限中心
     * 创建或更新 resource_entity（资源类型 MENU），返回 permResourceId
     *
     * @param tenantId 租户ID
     * @param menu     菜单实体
     * @return permission-center 的 resource_entity.id，失败返回 null
     */
    Long syncMenuToPermissionCenter(Long tenantId, SysMenu menu);

    /**
     * 批量同步菜单到权限中心
     *
     * @param tenantId 租户ID
     * @param menus    菜单列表
     * @return 同步成功数量
     */
    int batchSyncMenus(Long tenantId, Iterable<SysMenu> menus);

    /**
     * 从权限中心删除菜单资源
     *
     * @param tenantId     租户ID
     * @param permResourceId 权限中心的资源ID
     * @return 是否成功
     */
    boolean deleteMenuFromPermissionCenter(Long tenantId, Long permResourceId);

    /**
     * 生成菜单资源编码
     * 使用权限标识 permCode 作为资源编码
     *
     * @param menu 菜单实体
     * @return 资源编码
     */
    String generateResourceCode(SysMenu menu);
}