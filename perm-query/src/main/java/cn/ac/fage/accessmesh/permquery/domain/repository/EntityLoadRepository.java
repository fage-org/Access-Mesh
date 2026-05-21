package cn.ac.fage.accessmesh.permquery.domain.repository;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体加载仓储接口
 * <p>
 * 统一管理角色、资源、操作、条件等实体的批量加载。
 * </p>
 */
public interface EntityLoadRepository {

    /**
     * 批量加载角色
     */
    Map<Long, AbstractRole> loadRoles(Long tenantId, Set<Long> ids);

    /**
     * 批量加载资源
     */
    Map<Long, ResourceEntity> loadResources(Long tenantId, Set<Long> ids);

    /**
     * 批量加载操作权限（按ID）
     */
    Map<Long, OperationPermission> loadOperations(Long tenantId, Set<Long> ids);

    /**
     * 按资源类型批量加载操作权限
     */
    Map<Integer, List<OperationPermission>> loadOperationsByResourceTypes(Long tenantId, Set<Integer> resourceTypes);

    /**
     * 批量加载权限条件
     */
    Map<Long, PermissionCondition> loadConditions(Long tenantId, Set<Long> ids);
}