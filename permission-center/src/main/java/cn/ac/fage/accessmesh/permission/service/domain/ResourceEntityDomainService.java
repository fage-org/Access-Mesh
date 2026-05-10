package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源实体领域服务接口
 * <p>
 * 提供资源实体（ResourceEntity）的CRUD操作和树形结构查询功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API接口等。
 * 使用CTE递归查询高效获取祖先和子孙ID，避免N+1问题。
 * </p>
 */
public interface ResourceEntityDomainService {

    /**
     * 查询指定父资源下的子资源列表
     *
     * @param tenantId 租户ID
     * @param parentId 父资源ID
     * @return 子资源列表
     */
    List<ResourceEntity> listByParentId(Long tenantId, Long parentId);

    /**
     * 查询指定类型的资源列表
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @return 资源列表
     */
    List<ResourceEntity> listByType(Long tenantId, Integer resourceType);

    /**
     * 删除资源及其子孙资源和关联权限
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID
     */
    void deleteWithChildren(Long tenantId, Long resourceId);

    /**
     * 获取祖先资源ID列表
     * <p>
     * 获取指定资源的所有祖先ID（父、祖父等）。
     * 向上追溯到根节点或已删除实体为止。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 祖先资源ID列表
     */
    List<Long> getAncestorIds(Long tenantId, Long resourceEntityId);

    /**
     * 批量获取多个资源的祖先ID
     * <p>
     * 批量获取所有资源的祖先ID，返回Map按资源ID分组。
     * </p>
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 祖先ID映射表，key为资源ID，value为祖先ID列表
     */
    Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> resourceIds);

    /**
     * 获取子孙资源ID列表
     * <p>
     * 使用PostgreSQL CTE递归查询一次性获取所有子孙ID。
     * 不包含资源本身。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 子孙资源ID列表
     */
    List<Long> getDescendantIds(Long tenantId, Long resourceEntityId);

    /**
     * 获取子孙资源ID列表（包含自身）
     * <p>
     * 使用PostgreSQL CTE递归查询一次性获取所有子孙ID，包含资源本身。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 子孙资源ID列表（包含自身）
     */
    List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long resourceEntityId);

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

    /**
     * 根据类型和编码查找资源ID
     * <p>
     * 根据资源类型和编码查询资源实体ID。
     * 用于查找SERVICE资源等特定类型资源。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param code         资源编码
     * @return 资源ID，不存在返回null
     */
    Long findByTypeAndCode(Long tenantId, Integer resourceType, String code);
}