package cn.ac.fage.accessmesh.permquery.infrastructure.adapter;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体批量加载适配器
 * <p>
 * 封装 EntityBatchLoadDomainService，提供实体批量加载操作。
 * 用于加载权限相关的辅助实体信息。
 * </p>
 */
@Component
public class EntityBatchLoadAdapter {

    private final EntityBatchLoadDomainService entityBatchLoadService;

    public EntityBatchLoadAdapter(EntityBatchLoadDomainService entityBatchLoadService) {
        this.entityBatchLoadService = entityBatchLoadService;
    }

    // ===== 操作权限加载 =====

    /**
     * 批量加载操作权限（按ID）
     */
    public Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadOperations(tenantId, ids);
    }

    /**
     * 按资源类型批量加载操作权限
     */
    public Map<Integer, List<OperationPermission>> batchLoadOperationsByResourceTypes(
        Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadOperationsByResourceTypes(tenantId, resourceTypes);
    }

    // ===== 资源实体加载 =====

    /**
     * 批量加载资源实体
     */
    public Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadResources(tenantId, ids);
    }

    // ===== 角色加载 =====

    /**
     * 批量加载角色
     */
    public Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadRoles(tenantId, ids);
    }

    // ===== 条件加载 =====

    /**
     * 批量加载权限条件
     */
    public Map<Long, PermissionCondition> batchLoadConditions(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadConditions(tenantId, ids);
    }

    // ===== 业务域加载 =====

    /**
     * 批量加载业务域编码
     */
    public Map<Long, String> batchLoadDomainCodes(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadDomainCodes(tenantId, ids);
    }

    /**
     * 批量加载业务域实体
     */
    public Map<Long, BizDomain> batchLoadDomains(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadService.batchLoadDomains(tenantId, ids);
    }
}