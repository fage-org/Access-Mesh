package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源实体领域服务接口
 * <p>
 * 提供资源实体（ResourceEntity）的CRUD操作和树形结构查询功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API接口等。
 * 使用CTE递归查询高效获取子孙ID，避免N+1问题。
 * </p>
 */
public interface ResourceEntityDomainService {

    /**
     * 批量获取多个资源的子孙ID
     * <p>
     * 使用PostgreSQL CTE递归查询一次性获取所有子孙ID。
     * 返回Map按资源ID分组。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityIds 资源ID集合
     * @return 子孙ID映射表，key为资源ID，value为子孙ID列表
     */
    Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> resourceEntityIds);

    /**
     * 根据ID查询有效资源
     * <p>
     * 查询未删除的资源实体，包含租户校验。
     * 如果资源不存在、已删除或不属于租户，返回null。
     * </p>
     *
     * @param tenantId  租户ID
     * @param resourceId 资源ID
     * @return 资源实体，不存在或已删除返回null
     */
    ResourceEntity selectValidById(Long tenantId, Long resourceId);

    /**
     * 批量查询资源并构建映射表
     * <p>
     * 批量查询多个资源实体并构建ID到实体的映射表。
     * </p>
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 资源映射表，key为资源ID，value为资源实体
     */
    Map<Long, ResourceEntity> batchSelectByIdsMap(Long tenantId, Set<Long> resourceIds);

    /**
     * 批量查询有效资源列表
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 资源列表
     */
    List<ResourceEntity> selectValidByIds(Long tenantId, Set<Long> resourceIds);

    /**
     * 查询已存在的编码集合
     * <p>
     * 检查指定的编码是否已存在于租户下。
     * </p>
     *
     * @param tenantId 租户ID
     * @param codes    编码集合
     * @return 已存在的编码集合
     */
    Set<String> findExistingCodes(Long tenantId, Set<String> codes);

    /**
     * 批量软删除资源
     *
     * @param tenantId  租户ID
     * @param ids       待删除的资源ID列表
     * @param deletedAt 删除时间戳
     * @return 删除影响的行数
     */
    int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt);

    }