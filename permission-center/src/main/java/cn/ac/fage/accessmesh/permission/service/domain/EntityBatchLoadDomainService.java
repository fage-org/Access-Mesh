package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.util.Map;
import java.util.Set;

/**
 * 实体批量加载领域服务接口
 * <p>
 * 提供统一的实体批量加载方法，避免N+1查询问题，减少代码重复。
 * 所有方法均强制执行租户隔离和软删除过滤。
 * </p>
 */
public interface EntityBatchLoadDomainService {

    /**
     * 批量加载操作权限
     * <p>
     * 根据ID集合批量查询操作权限，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离过滤（不可为null）
     * @param ids      操作权限ID集合
     * @return ID到OperationPermission的映射，ids为空时返回空Map
     */
    Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids);

    /**
     * 批量加载资源实体
     * <p>
     * 根据ID集合批量查询资源实体，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离过滤（不可为null）
     * @param ids      资源实体ID集合
     * @return ID到ResourceEntity的映射，ids为空时返回空Map
     */
    Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids);

    /**
     * 批量加载抽象角色
     * <p>
     * 根据ID集合批量查询角色，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离过滤（不可为null）
     * @param ids      角色ID集合
     * @return ID到AbstractRole的映射，ids为空时返回空Map
     */
    Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids);

    /**
     * 批量加载权限条件
     * <p>
     * 根据ID集合批量查询权限条件，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离过滤（不可为null）
     * @param ids      条件ID集合
     * @return ID到PermissionCondition的映射，ids为空时返回空Map
     */
    Map<Long, PermissionCondition> batchLoadConditions(Long tenantId, Set<Long> ids);

    /**
     * 批量加载业务域编码
     * <p>
     * 根据ID集合批量查询业务域编码，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离过滤（不可为null）
     * @param ids      业务域ID集合
     * @return ID到业务域编码的映射，ids为空时返回空Map
     */
    Map<Long, String> batchLoadDomainCodes(Long tenantId, Set<Long> ids);

    /**
     * 批量加载业务域
     * <p>
     * 根据ID集合批量查询业务域实体，自动过滤租户和软删除记录
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离过滤（不可为null）
     * @param ids      业务域ID集合
     * @return ID到BizDomain的映射，ids为空时返回空Map
     */
    Map<Long, BizDomain> batchLoadDomains(Long tenantId, Set<Long> ids);
}