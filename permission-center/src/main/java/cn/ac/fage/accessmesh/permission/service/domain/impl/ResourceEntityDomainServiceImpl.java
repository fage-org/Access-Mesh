package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源实体领域服务实现类
 * <p>
 * 提供资源实体（ResourceEntity）的CRUD操作和树形结构查询功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API接口等。
 * 核心功能包括：
 * - 按父ID、类型查询资源列表
 * - 删除资源及其子孙资源和关联权限
 * - 获取祖先ID和子孙ID（使用CTE递归查询）
 * - 批量查询、批量软删除
 * 使用CTE递归查询高效获取祖先和子孙ID，避免N+1问题。
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
     * 查询指定父资源下的子资源列表
     *
     * @param tenantId 租户ID
     * @param parentId 父资源ID，null表示根级
     * @return 子资源列表
     */
    @Override
    public List<ResourceEntity> listByParentId(Long tenantId, Long parentId) {
        return resourceEntityMapper.selectByParentId(tenantId, parentId);
    }

    /**
     * 查询指定类型的资源列表
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @return 资源列表
     */
    @Override
    public List<ResourceEntity> listByType(Long tenantId, Integer resourceType) {
        return resourceEntityMapper.selectByType(tenantId, resourceType);
    }

    /**
     * 删除资源及其子孙资源和关联权限
     * <p>
     * 软删除指定资源及其所有子孙资源，同时清理关联的角色权限。
     * 使用CTE递归查询一次性获取所有子孙ID，然后批量软删除，
     * 避免逐级查询的N+1问题。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithChildren(Long tenantId, Long resourceId) {
        LocalDateTime now = LocalDateTime.now();
        // 验证实体存在且属于租户
        ResourceEntity entity = selectValidById(tenantId, resourceId);
        if (entity == null) return;

        // 使用CTE一次性获取所有子孙ID（包含自身）
        List<Long> allIds = resourceEntityMapper.selectDescendantIdsIncludingSelf(tenantId, resourceId);

        // 获取整个子树的所有角色权限ID
        List<Long> permIds = rolePermMapper.selectValidPermIdsByResourceIds(tenantId, allIds);

        // 批量软删除所有子孙（包含自身）
        resourceEntityMapper.softDeleteBatch(tenantId, allIds, now);

        // 批量软删除关联的角色权限
        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }
    }

    /**
     * 获取祖先资源ID列表
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 祖先资源ID列表，无祖先返回空列表
     */
    @Override
    public List<Long> getAncestorIds(Long tenantId, Long resourceEntityId) {
        if (resourceEntityId == null) {
            return List.of();
        }
        List<Long> ancestorIds = resourceEntityMapper.selectAncestorIds(tenantId, resourceEntityId);
        return ancestorIds != null ? ancestorIds : List.of();
    }

    /**
     * 批量获取多个资源的祖先ID
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 祖先ID映射表，key为资源ID，value为祖先ID列表
     */
    @Override
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ResourceEntityMapper.AncestorResult> results = resourceEntityMapper.selectAncestorIdsBatch(tenantId, resourceIds);

        Map<Long, List<Long>> result = new HashMap<>();
        for (Long resourceId : resourceIds) {
            result.put(resourceId, new ArrayList<>());
        }

        for (ResourceEntityMapper.AncestorResult ar : results) {
            result.computeIfAbsent(ar.getResourceId(), k -> new ArrayList<>()).add(ar.getAncestorId());
        }

        return result;
    }

    /**
     * 获取子孙资源ID列表
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 子孙资源ID列表，无子孙返回空列表
     */
    @Override
    public List<Long> getDescendantIds(Long tenantId, Long resourceEntityId) {
        if (resourceEntityId == null) {
            return List.of();
        }
        List<Long> ids = resourceEntityMapper.selectDescendantIds(tenantId, resourceEntityId);
        return ids != null ? ids : List.of();
    }

    /**
     * 获取子孙资源ID列表（包含自身）
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID
     * @return 子孙资源ID列表（包含自身），不存在返回空列表
     */
    @Override
    public List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long resourceEntityId) {
        if (resourceEntityId == null) {
            return List.of();
        }
        List<Long> ids = resourceEntityMapper.selectDescendantIdsIncludingSelf(tenantId, resourceEntityId);
        return ids != null ? ids : List.of();
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