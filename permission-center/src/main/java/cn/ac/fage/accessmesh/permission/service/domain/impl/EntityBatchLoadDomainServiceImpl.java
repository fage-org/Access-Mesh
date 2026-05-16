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
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实体批量加载领域服务实现类
 * <p>
 * 提供统一的实体批量加载方法，避免N+1查询问题，减少代码重复。
 * 所有方法均强制执行租户隔离和软删除过滤。
 * </p>
 */
@Service
public class EntityBatchLoadDomainServiceImpl implements EntityBatchLoadDomainService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermissionConditionMapper permissionConditionMapper;
    private final BizDomainMapper bizDomainMapper;

    /**
     * 构造函数注入所有Mapper依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     * @param resourceEntityMapper      资源实体数据访问层
     * @param abstractRoleMapper        抽象角色数据访问层
     * @param permissionConditionMapper 权限条件数据访问层
     * @param bizDomainMapper           业务域数据访问层
     */
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

    /**
     * 批量加载操作权限
     * <p>
     * 根据ID集合批量查询操作权限，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      操作权限ID集合
     * @return ID到OperationPermission的映射
     */
    @Override
    public Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return operationPermissionMapper.selectValidByIds(tenantId, ids)
                .stream().collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));
    }

    /**
     * 批量加载资源实体
     * <p>
     * 根据ID集合批量查询资源实体，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      资源实体ID集合
     * @return ID到ResourceEntity的映射
     */
    @Override
    public Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return resourceEntityMapper.selectValidByIds(tenantId, ids)
                .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));
    }

    /**
     * 批量加载抽象角色
     * <p>
     * 根据ID集合批量查询角色，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      角色ID集合
     * @return ID到AbstractRole的映射
     */
    @Override
    public Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return abstractRoleMapper.selectValidByIds(tenantId, ids)
                .stream().collect(Collectors.toMap(AbstractRole::getId, role -> role, (a, b) -> a));
    }

    /**
     * 批量加载权限条件
     * <p>
     * 根据ID集合批量查询权限条件，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      条件ID集合
     * @return ID到PermissionCondition的映射
     */
    @Override
    public Map<Long, PermissionCondition> batchLoadConditions(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return permissionConditionMapper.selectValidByIds(tenantId, ids)
                .stream().collect(Collectors.toMap(PermissionCondition::getId, c -> c, (a, b) -> a));
    }

    /**
     * 批量加载业务域编码
     * <p>
     * 根据ID集合批量查询业务域编码，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      业务域ID集合
     * @return ID到业务域编码的映射
     */
    @Override
    public Map<Long, String> batchLoadDomainCodes(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return bizDomainMapper.selectValidByIds(tenantId, ids)
                .stream().collect(Collectors.toMap(BizDomain::getId, BizDomain::getCode, (a, b) -> a));
    }

    /**
     * 批量加载业务域
     * <p>
     * 根据ID集合批量查询业务域实体，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids      业务域ID集合
     * @return ID到BizDomain的映射
     */
    @Override
    public Map<Long, BizDomain> batchLoadDomains(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return bizDomainMapper.selectValidByIds(tenantId, ids)
                .stream().collect(Collectors.toMap(BizDomain::getId, d -> d, (a, b) -> a));
    }
}
