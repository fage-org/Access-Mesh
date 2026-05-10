package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;

import java.util.List;

/**
 * 资源依赖领域服务接口
 * <p>
 * 提供资源依赖关系的自动授权和清理功能。
 * 资源依赖定义了权限级联规则：当用户对源资源执行某操作时，
 * 如果触发依赖条件，系统自动授予目标资源的相应权限。
 * 自动授予的权限标记grantSource为AUTO_DEP，便于追踪和清理。
 * </p>
 */
public interface ResourceDependencyDomainService {

    /**
     * 处理资源依赖触发
     * <p>
     * 当授予某资源的权限时，检查是否存在依赖规则需要自动授权。
     * 遍历源资源的所有依赖规则，如果操作位触发条件满足且autoGrant为true，
     * 自动授予目标资源的相应权限。
     * </p>
     *
     * @param tenantId        租户ID
     * @param roleId          角色ID
     * @param resourceEntityId 源资源实体ID
     * @param operationBits   授予的操作位掩码
     */
    void processDependencies(Long tenantId, Long roleId, Long resourceEntityId, Long operationBits);

    /**
     * 清理资源依赖级联权限
     * <p>
     * 当撤销某资源的权限时，清理所有由此资源自动授权的级联权限。
     * 查询grantSource为AUTO_DEP且源资源匹配的权限记录，批量软删除。
     * </p>
     *
     * @param tenantId        租户ID
     * @param roleId          角色ID
     * @param resourceEntityId 源资源实体ID
     */
    void cleanupDependencies(Long tenantId, Long roleId, Long resourceEntityId);

    /**
     * 批量插入时预计算自动授权
     * <p>
     * 在批量授予权限前，预先计算需要自动授权的依赖权限。
     * 使用批量查询和缓存优化，避免嵌套循环中的N+1查询问题。
     * 返回需要额外插入的自动授权权限列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param toInsert 待插入的权限列表
     * @return 需要自动授权的权限列表
     */
    List<RoleResourcePermission> autoGrantForInsert(Long tenantId, Long roleId, List<RoleResourcePermission> toInsert);
}