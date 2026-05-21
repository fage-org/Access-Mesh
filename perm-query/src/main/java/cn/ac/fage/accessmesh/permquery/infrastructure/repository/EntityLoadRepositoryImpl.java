package cn.ac.fage.accessmesh.permquery.infrastructure.repository;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permquery.domain.repository.EntityLoadRepository;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.EntityBatchLoadAdapter;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体加载仓储实现
 * <p>
 * 通过 EntityBatchLoadAdapter 封装对外部服务的调用。
 * </p>
 */
@Repository
public class EntityLoadRepositoryImpl implements EntityLoadRepository {

    private final EntityBatchLoadAdapter entityBatchLoadAdapter;

    public EntityLoadRepositoryImpl(EntityBatchLoadAdapter entityBatchLoadAdapter) {
        this.entityBatchLoadAdapter = entityBatchLoadAdapter;
    }

    @Override
    public Map<Long, AbstractRole> loadRoles(Long tenantId, Set<Long> ids) {
        if (tenantId == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadAdapter.batchLoadRoles(tenantId, ids);
    }

    @Override
    public Map<Long, ResourceEntity> loadResources(Long tenantId, Set<Long> ids) {
        if (tenantId == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadAdapter.batchLoadResources(tenantId, ids);
    }

    @Override
    public Map<Long, OperationPermission> loadOperations(Long tenantId, Set<Long> ids) {
        if (tenantId == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadAdapter.batchLoadOperations(tenantId, ids);
    }

    @Override
    public Map<Integer, List<OperationPermission>> loadOperationsByResourceTypes(
        Long tenantId, Set<Integer> resourceTypes) {
        if (tenantId == null || resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadAdapter.batchLoadOperationsByResourceTypes(tenantId, resourceTypes);
    }

    @Override
    public Map<Long, PermissionCondition> loadConditions(Long tenantId, Set<Long> ids) {
        if (tenantId == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return entityBatchLoadAdapter.batchLoadConditions(tenantId, ids);
    }
}