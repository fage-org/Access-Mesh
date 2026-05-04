package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;

/**
 * 组织同步处理器
 * 将 admin-service 的组织同步到 permission-center 的 resource_entity
 */
public interface OrgSyncHandler {

    /**
     * 同步组织到权限中心
     * 创建或更新 resource_entity（资源类型 ORG），返回 permResourceId
     *
     * @param tenantId 租户ID
     * @param org      组织实体
     * @return permission-center 的 resource_entity.id，失败返回 null
     */
    Long syncOrgToPermissionCenter(Long tenantId, SysOrg org);

    /**
     * 批量同步组织到权限中心
     *
     * @param tenantId 租户ID
     * @param orgs     组织列表
     * @return 同步成功数量
     */
    int batchSyncOrgs(Long tenantId, Iterable<SysOrg> orgs);

    /**
     * 从权限中心删除组织资源
     *
     * @param tenantId     租户ID
     * @param permResourceId 权限中心的资源ID
     * @return 是否成功
     */
    boolean deleteOrgFromPermissionCenter(Long tenantId, Long permResourceId);

    /**
     * 生成组织资源编码
     *
     * @param org 组织实体
     * @return 资源编码
     */
    String generateResourceCode(SysOrg org);
}