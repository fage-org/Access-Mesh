package cn.ac.fage.accessmesh.access.permission.service.domain;

import java.util.List;

/**
 * 管理事实对应的本地权限投影。
 * <p>
 * 只写权限计算表，不写 sync_metadata。owner_service_code 固定为 access-service。
 * 事务由 access.application 声明。
 * </p>
 */
public interface LocalProjectionDomainService {

    /**
     * UPSERT abstract_user(ADMIN_USER) + resource_entity(ADMIN_USER)。
     *
     * @return abstract_user.id
     */
    Long upsertAdminUser(Long tenantId, Long sysUserId, String name, boolean enabled, String extraJson);

    /**
     * 停用 abstract_user 与 ADMIN_USER 资源。
     */
    void disableAdminUser(Long tenantId, Long sysUserId);

    /**
     * 软删除 abstract_user 与 ADMIN_USER 资源。
     */
    void deleteAdminUser(Long tenantId, Long sysUserId);

    /**
     * UPSERT abstract_role(ORG|POSITION) + resource_entity(ADMIN_ORG)。
     *
     * @return abstract_role.id
     */
    Long upsertAdminOrg(Long tenantId, Long sysOrgId, String orgType, String name,
                        Long parentOrgId, Integer status, Integer sortOrder, String extraJson);

    /**
     * 软删除组织角色与 ADMIN_ORG 资源。
     */
    void deleteAdminOrg(Long tenantId, Long sysOrgId, String orgType);

    /**
     * UPSERT resource_entity(ADMIN_MENU)。BUTTON 行由调用方短路。
     *
     * @return resource_entity.id
     */
    Long upsertAdminMenu(Long tenantId, Long sysMenuId, String name, Long parentMenuId,
                         Integer status, Integer sortOrder);

    /**
     * 软删除 ADMIN_MENU 资源。
     */
    void deleteAdminMenu(Long tenantId, Long sysMenuId);

    /**
     * BIND sys_user_org 对应的 user_role。
     *
     * @return user_role.id（投影主键，供变更日志 entityId 使用）
     */
    Long bindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode);

    /**
     * UNBIND sys_user_org 对应的 user_role。
     *
     * @return user_role.id（软删前取得；投影缺失返回 null）
     */
    Long unbindUserOrg(Long tenantId, Long sysUserId, Long sysOrgId, String roleTypeCode);

    /**
     * 批量 UNBIND（T-ACCESS-005 评审 P2：消除删除路径循环单条 unbind 的 N+1）。
     * 一次批量加载 abstract_user / abstract_role / relationRole / user_role，
     * 按 (sysUserId, sysOrgId, roleTypeCode) 三元组内存匹配后批量软删。
     */
    void batchUnbindUserOrg(Long tenantId, List<UserOrgBindKey> keys);

    /**
     * 批量解绑键：管理事实侧 (sys_user.id, sys_org.id, 角色类型码)。
     */
    record UserOrgBindKey(Long sysUserId, Long sysOrgId, String roleTypeCode) {}

    /**
     * 按 sys_user.id 定位 abstract_user.id，供缓存失效使用。
     */
    Long findAdminUserId(Long tenantId, Long sysUserId);

    /**
     * 按 sys_org.id 定位 abstract_role.id，供缓存失效使用。
     */
    Long findAdminOrgRoleId(Long tenantId, Long sysOrgId, String orgType);

    /**
     * 按 sys_menu.id 定位 resource_entity.id（ADMIN_MENU 投影主键），供变更日志 entityId 使用。
     */
    Long findAdminMenuResourceId(Long tenantId, Long sysMenuId);
}
