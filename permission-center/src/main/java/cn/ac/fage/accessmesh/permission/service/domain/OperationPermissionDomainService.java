package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;

import java.util.List;
import java.util.Set;

/**
 * 操作权限领域服务接口
 * <p>
 * 提供操作权限（OperationPermission）的基础数据访问操作。
 * 操作权限定义了系统支持的各种操作类型，如查看、编辑、删除、管理等。
 * 每个操作权限包含二进制位用于位运算权限匹配，继承掩码用于权限继承计算。
 * </p>
 */
public interface OperationPermissionDomainService {

    /**
     * 根据ID查询操作权限
     *
     * @param id 操作权限ID
     * @return 操作权限实体，不存在返回null
     */
    OperationPermission selectOneById(Long id);

    /**
     * 插入操作权限
     *
     * @param entity 操作权限实体
     */
    void insert(OperationPermission entity);

    /**
     * 更新操作权限
     *
     * @param entity 操作权限实体
     * @return 更新影响的行数
     */
    int update(OperationPermission entity);

    /**
     * 根据ID查询有效操作权限
     * <p>
     * 查询未删除的操作权限实体，包含租户校验。
     * 如果操作权限不存在、已删除或不属于租户，返回null。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @return 操作权限实体，不存在或已删除返回null
     */
    OperationPermission selectValidById(Long tenantId, Long operationId);

    /**
     * 根据租户ID、资源类型集合和操作码集合查询操作权限列表
     *
     * @param tenantId         租户ID
     * @param resourceTypeValues 资源类型值集合
     * @param operationCodes   操作码集合
     * @return 操作权限列表
     */
    List<OperationPermission> selectByTenantResourceTypesAndOpCodes(Long tenantId,
                                                                     Set<Integer> resourceTypeValues,
                                                                     Set<String> operationCodes);

    /**
     * 根据租户ID查询操作权限列表（可选资源类型过滤）
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值，可为null（不过滤）
     * @return 操作权限列表
     */
    List<OperationPermission> selectByTenantAndResourceType(Long tenantId, Integer resourceType);
}