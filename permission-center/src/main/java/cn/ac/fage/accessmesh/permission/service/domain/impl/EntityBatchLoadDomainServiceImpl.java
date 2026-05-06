package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.PermissionConditionTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;

/**
 * Implementation of EntityBatchLoadDomainService.
 * Provides unified batch loading methods with tenant isolation and soft-delete filtering.
 */
@Service
public class EntityBatchLoadDomainServiceImpl implements EntityBatchLoadDomainService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final BizDomainMapper bizDomainMapper;

    public EntityBatchLoadDomainServiceImpl(
            OperationPermissionMapper operationPermissionMapper,
            ResourceEntityMapper resourceEntityMapper,
            AbstractRoleMapper abstractRoleMapper,
            PermissionConditionMapper permissionConditionMapper,
            BizDomainMapper bizDomainMapper) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.permissionConditionMapper = permissionConditionMapper;
        this.bizDomainMapper = bizDomainMapper;
    }

    @Override
    public Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return operationPermissionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                        .and(OperationPermissionTableDef.OPERATION_PERMISSION.ID.in(ids))
                        .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));
    }

    @Override
    public Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.ID.in(ids))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));
    }

    @Override
    public Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                        .and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(ids))
                        .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(AbstractRole::getId, role -> role, (a, b) -> a));
    }

    @Override
    public Map<Long, PermissionCondition> batchLoadConditions(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return permissionConditionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(PermissionConditionTableDef.PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                        .and(PermissionConditionTableDef.PERMISSION_CONDITION.ID.in(ids))
                        .and(PermissionConditionTableDef.PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(PermissionCondition::getId, c -> c, (a, b) -> a));
    }

    @Override
    public Map<Long, String> batchLoadDomainCodes(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return bizDomainMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(BizDomainTableDef.BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                        .and(BizDomainTableDef.BIZ_DOMAIN.ID.in(ids))
                        .and(BizDomainTableDef.BIZ_DOMAIN.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(BizDomain::getId, BizDomain::getCode, (a, b) -> a));
    }

    @Override
    public Map<Long, BizDomain> batchLoadDomains(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return bizDomainMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(BizDomainTableDef.BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                        .and(BizDomainTableDef.BIZ_DOMAIN.ID.in(ids))
                        .and(BizDomainTableDef.BIZ_DOMAIN.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(BizDomain::getId, d -> d, (a, b) -> a));
    }
}