package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源实体领域服务实现类
 * <p>
 * 提供资源实体（ResourceEntity）的CRUD操作和树形结构查询功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API接口等。
 * 核心功能包括：
 * - 删除资源及其子孙资源和关联权限
 * - 获取子孙ID（使用CTE递归查询）
 * - 批量查询、批量软删除
 * 使用CTE递归查询高效获取子孙ID，避免N+1问题。
 * 所有写操作均使用事务保证数据一致性。
 * </p>
 */
@Service
public class ResourceEntityDomainServiceImpl implements ResourceEntityDomainService {

    private final ResourceEntityMapper resourceEntityMapper;
    private final RoleResourcePermissionMapper rolePermMapper;

    /**
     * 构造函数注入依赖
     *
     * @param resourceEntityMapper 资源实体数据访问层
     * @param rolePermMapper       角色权限数据访问层
     */
    public ResourceEntityDomainServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                            RoleResourcePermissionMapper rolePermMapper) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.rolePermMapper = rolePermMapper;
    }

    /**
     * 批量获取多个资源的子孙ID
     *
     * @param tenantId         租户ID
     * @param resourceEntityIds 资源ID集合
     * @return 子孙ID映射表，key为资源ID，value为子孙ID列表
     */
    @Override
    public Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> resourceEntityIds) {
        if (resourceEntityIds == null || resourceEntityIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<Long>> result = new HashMap<>();
        for (Long id : resourceEntityIds) {
            result.put(id, new ArrayList<>());
        }

        List<ResourceEntityMapper.DescendantResult> descendants = resourceEntityMapper.selectDescendantIdsBatch(tenantId, resourceEntityIds);

        for (ResourceEntityMapper.DescendantResult dr : descendants) {
            result.computeIfAbsent(dr.getResourceId(), k -> new ArrayList<>()).add(dr.getDescendantId());
        }

        return result;
    }

    /**
     * 根据ID查询有效资源
     *
     * @param tenantId  租户ID
     * @param resourceId 资源ID
     * @return 资源实体，不存在或已删除返回null
     */
    @Override
    public ResourceEntity selectValidById(Long tenantId, Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        return resourceEntityMapper.selectValidById(tenantId, resourceId);
    }

    /**
     * 批量查询资源并构建映射表
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 资源映射表，key为资源ID，value为资源实体
     */
    @Override
    public Map<Long, ResourceEntity> batchSelectByIdsMap(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ResourceEntity> entities = resourceEntityMapper.selectValidByIds(tenantId, resourceIds);
        Map<Long, ResourceEntity> result = new HashMap<>();
        for (ResourceEntity entity : entities) {
            result.put(entity.getId(), entity);
        }
        return result;
    }

    /**
     * 批量查询有效资源列表
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 资源列表，空集合返回空列表
     */
    @Override
    public List<ResourceEntity> selectValidByIds(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return resourceEntityMapper.selectValidByIds(tenantId, resourceIds);
    }

    /**
     * 查询已存在的编码集合
     *
     * @param tenantId 租户ID
     * @param codes    编码集合
     * @return 已存在的编码集合
     */
    @Override
    public Set<String> findExistingCodes(Long tenantId, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> existingCodes = resourceEntityMapper.selectExistingCodes(tenantId, codes);
        return existingCodes != null ? existingCodes : Collections.emptySet();
    }

    /**
     * 类型下是否存在有效资源行（T-PERM-052 类型所有权声明变更守卫：无有效行才可改）。
     *
     * @param tenantId     租户ID
     * @param resourceType resource_type 内部类型值
     * @return true=存在 delete_flag=0 的行
     */
    @Override
    public boolean hasValidRowsOfType(Long tenantId, Integer resourceType) {
        if (resourceType == null) {
            return false;
        }
        return resourceEntityMapper.existsValidByType(tenantId, resourceType) > 0;
    }

    @Override
    public Set<Integer> findTypesWithValidRows(Long tenantId, Collection<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(resourceEntityMapper.selectDistinctTypesWithValidRows(tenantId, resourceTypes));
    }

    /**
     * 批量软删除资源
     *
     * @param tenantId  租户ID
     * @param ids       待删除的资源ID列表
     * @param deletedAt 删除时间
     * @return 删除影响的行数
     */
    @Override
    public int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return resourceEntityMapper.softDeleteBatch(tenantId, ids, deletedAt);
    }

    }