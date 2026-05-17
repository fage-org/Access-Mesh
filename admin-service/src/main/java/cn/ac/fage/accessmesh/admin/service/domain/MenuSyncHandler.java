package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;

/**
 * 菜单同步处理器接口
 * <p>
 * 将 admin-service 的菜单数据同步到 permission-center 的 resource_entity 表。
 * 实现跨服务的菜单资源同步机制，确保权限系统中的菜单资源与后台管理菜单保持一致。
 * 使用 Feign 远程调用实现同步操作。
 * </p>
 */
public interface MenuSyncHandler {

    /**
     * 同步单个菜单到权限中心
     * <p>
     * 在 permission-center 创建或更新对应的 resource_entity 记录。
     * 资源类型设置为 MENU，资源编码使用菜单的权限标识（permCode）。
     * 若菜单已存在则更新，不存在则创建。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menu     菜单实体，包含菜单的基本信息和权限标识
     * @return permission-center 中的 resource_entity.id，同步失败返回 null
     */
    Long syncMenuToPermissionCenter(Long tenantId, SysMenu menu);

    /**
     * 生成菜单资源编码
     * <p>
     * 使用菜单的权限标识（permCode）作为权限中心的资源编码。
     * 确保菜单资源在权限系统中有唯一的标识符。
     * </p>
     *
     * @param menu 菜单实体
     * @return 菜单资源编码字符串
     */
    String generateResourceCode(SysMenu menu);
}