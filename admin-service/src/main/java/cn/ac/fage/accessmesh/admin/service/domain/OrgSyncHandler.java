package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;

/**
 * 组织同步处理器接口
 * <p>
 * 将 admin-service 的组织数据同步到 permission-center 的 resource_entity 表。
 * 实现跨服务的组织资源同步机制，确保权限系统中的组织资源与后台管理组织保持一致。
 * 使用组织ID作为外部标识，支持层级结构的同步。
 * </p>
 */
public interface OrgSyncHandler {

    /**
     * 同步单个组织到权限中心
     * <p>
     * 在 permission-center 创建或更新对应的 resource_entity 记录。
     * 资源类型设置为 ORG，资源编码使用组织编码（orgCode）。
     * 若组织已存在则更新，不存在则创建。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param org      组织实体，包含组织的基本信息和层级关系
     * @return permission-center 中的 resource_entity.id，同步失败返回 null
     */
    Long syncOrgToPermissionCenter(Long tenantId, SysOrg org);

    /**
     * 生成组织资源编码
     * <p>
     * 使用组织的编码作为权限中心的资源编码。
     * 确保组织资源在权限系统中有唯一的标识符。
     * </p>
     *
     * @param org 组织实体
     * @return 组织资源编码字符串
     */
    String generateResourceCode(SysOrg org);
}