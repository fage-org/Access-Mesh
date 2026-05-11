package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingUpdateReq;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceCreateReq;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceUpdateReq;

import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;

import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;

import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;

import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp.ResourceTreeNode;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;

import cn.ac.fage.accessmesh.permission.enums.ResourceType;

import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;

import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;

import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;

import cn.ac.fage.accessmesh.permission.service.AuthorizationService;

import cn.ac.fage.accessmesh.permission.service.ResourceManageService;

import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;

import cn.ac.fage.accessmesh.permission.service.domain.ResourceApiMappingDomainService;

import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;

import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;

import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.dto.query.DomainTypeFilter;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;

import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;


import cn.ac.fage.accessmesh.permission.util.OperatorContext;

import cn.ac.fage.accessmesh.permission.util.OperatorUtil;

import cn.ac.fage.accessmesh.permission.util.PermissionConstants;

import cn.ac.fage.accessmesh.permission.util.TreeBuilder;

import com.mybatisflex.core.query.QueryWrapper;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.HashSet;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import java.util.Set;

import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;

/**
 * 资源管理服务实现类
 * <p>
 * 提供资源实体的CRUD操作、树结构查询、批量操作等功能。
 * 资源实体是权限系统中的受保护对象，如菜单、按钮、API接口等。
 * 同时提供资源与API接口的映射关系管理功能。
 * 所有操作均进行权限校验，确保操作者有相应权限。
 * </p>
 */
@Service
public class ResourceManageServiceImpl implements ResourceManageService {

    private static final Logger log = LoggerFactory.getLogger(ResourceManageServiceImpl.class);

    private final ResourceEntityMapper resourceEntityMapper;

    private final ResourceApiMappingMapper apiMappingMapper;

    private final ResourceEntityDomainService resourceEntityDomainService;

    private final ResourceApiMappingDomainService resourceApiMappingDomainService;

    private final TypeResolutionService typeResolutionService;

    private final DomainClassifyService domainClassifyService;

    private final OperationLogDomainService operationLogDomainService;

    private final AuthorizationService authorizationService;

    private final PermQueryEngine engine;

    private final RoleResourcePermissionMapper rolePermMapper;

    // TODO: 构造函数依赖过多(9个)，建议拆分资源实体管理和API映射管理职责
    // 优先级：P3（低优先级，可关注但不强制整改）

    /**
     * 构造函数注入依赖
     *
     * @param resourceEntityMapper          资源实体Mapper
     * @param apiMappingMapper              API映射Mapper
     * @param resourceEntityDomainService   资源实体领域服务
     * @param resourceApiMappingDomainService API映射领域服务
     * @param typeResolutionService         类型解析服务
     * @param operationLogDomainService     操作日志领域服务
     * @param authorizationService          授权服务
     * @param engine                        权限查询引擎
     * @param rolePermMapper                角色权限Mapper
     */
    public ResourceManageServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                     ResourceApiMappingMapper apiMappingMapper,
                                     ResourceEntityDomainService resourceEntityDomainService,
                                     ResourceApiMappingDomainService resourceApiMappingDomainService,
                                     TypeResolutionService typeResolutionService,
                                     DomainClassifyService domainClassifyService,
                                     OperationLogDomainService operationLogDomainService,
                                     AuthorizationService authorizationService,
                                     PermQueryEngine engine,
                                     RoleResourcePermissionMapper rolePermMapper) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.resourceApiMappingDomainService = resourceApiMappingDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.operationLogDomainService = operationLogDomainService;
        this.authorizationService = authorizationService;
        this.engine = engine;
        this.rolePermMapper = rolePermMapper;
    }

    /**
     * 创建单个资源实体
     * <p>
     * 在指定租户和域下创建新的资源实体。
     * 需要CREATE权限才能执行此操作。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        资源创建请求，包含资源基本信息
     * @param operatorId 操作者ID，可选，默认为系统操作
     * @return 创建成功的资源详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限检查：需要有资源的CREATE权限
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无创建资源的权限");
        }

        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(req.parentId());
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", req.resourceTypeCode());
        if (resourceType == null) {
            throw new IllegalArgumentException("未知的resourceTypeCode: " + req.resourceTypeCode());
        }
        entity.setResourceType(resourceType);
        entity.setCode(req.code());
        entity.setCodeType(req.codeType());
        entity.setName(req.name());
        entity.setPath(req.path());
        entity.setStatus(req.status() != null ? req.status() : 1);
        entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        entity.setExtra(req.extra());
        entity.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleteFlag(0L);
        resourceEntityMapper.insert(entity);
        return toResourceResp(entity);
    }

    /**
     * 批量创建资源实体
     * <p>
     * 批量创建多个资源实体，使用批量查询优化避免N+1问题。
     * 需要CREATE权限才能执行此操作。
     * 支持验证父资源存在性和编码唯一性。
     * </p>
     *
     * @param tenantId   租户ID
     * @param reqs       资源创建请求列表
     * @param operatorId 操作者ID，可选
     * @return 创建成功的资源列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // 权限检查
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无批量创建资源的权限");
        }

        if (reqs == null || reqs.isEmpty()) {
            return List.of();
        }

        // 1. 批量收集所有父ID和编码
        Set<Long> allParentIds = reqs.stream()
            .map(ResourceCreateReq::parentId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());
        Set<String> allCodes = reqs.stream()
            .map(ResourceCreateReq::code)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());

        // 2. 批量查询父资源和已存在的编码（2次查询，避免N+1）
        Map<Long, ResourceEntity> parentMap = resourceEntityDomainService.batchSelectByIdsMap(tenantId, allParentIds);
        Set<String> existingCodes = resourceEntityDomainService.findExistingCodes(tenantId, allCodes);

        // 3. 批量解析资源类型编码（避免N+1）
        Set<String> typeCodes = reqs.stream()
            .map(ResourceCreateReq::resourceTypeCode)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> typeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", typeCodes);

        // 4. 构建待插入实体（内存验证和构建）
        LocalDateTime now = LocalDateTime.now();
        List<ResourceEntity> toInsert = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < reqs.size(); i++) {
            ResourceCreateReq req = reqs.get(i);

            // 验证父资源存在
            if (req.parentId() != null && req.parentId() > 0 && !parentMap.containsKey(req.parentId())) {
                errors.add("req[" + i + "]: 父资源不存在: " + req.parentId());
                continue;
            }

            // 验证编码唯一性
            if (req.code() != null && !req.code().isBlank() && existingCodes.contains(req.code())) {
                errors.add("req[" + i + "]: 编码已存在: " + req.code());
                continue;
            }

            // 解析资源类型
            Integer resourceType = typeValueMap.get(req.resourceTypeCode());
            if (resourceType == null) {
                errors.add("req[" + i + "]: 未知的resourceTypeCode: " + req.resourceTypeCode());
                continue;
            }

            // 构建实体
            ResourceEntity entity = new ResourceEntity();
            entity.setTenantId(tenantId);
            entity.setParentId(req.parentId());
            entity.setResourceType(resourceType);
            entity.setCode(req.code());
            entity.setCodeType(req.codeType());
            entity.setName(req.name());
            entity.setPath(req.path());
            entity.setStatus(req.status() != null ? req.status() : 1);
            entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
            entity.setExtra(req.extra());
            entity.setCreatedBy(operatorId);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setDeleteFlag(0L);
            toInsert.add(entity);
        }

        // 报告验证错误
        if (!errors.isEmpty()) {
            log.warn("批量创建资源验证错误: {}", errors);
        }

        // 5. 批量插入（单次操作，避免N+1）
        if (!toInsert.isEmpty()) {
            resourceEntityMapper.insertBatch(toInsert);
        }

        // 6. 返回结果
        return toInsert.stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    /**
     * 获取资源详情
     * <p>
     * 根据资源ID查询资源的完整信息。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID
     * @return 资源详情，不存在返回null
     */
    @Override
    public ResourceResp getResource(Long tenantId, Long resourceId) {
        ResourceEntity entity = resourceEntityMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ResourceEntityTableDef.RESOURCE_ENTITY.ID.eq(resourceId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        return entity != null ? toResourceResp(entity) : null;
    }

    /**
     * 更新资源信息
     * <p>
     * 更新资源的编码、名称、路径、状态等属性。
     * 需要有对该资源的MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        资源更新请求，包含资源ID和新属性值
     * @param operatorId 操作者ID，可选
     * @return 更新后的资源详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.id());
        if (entity == null) {
            throw new IllegalArgumentException("资源不存在: " + req.id());
        }

        // 实例级权限检查：操作者需要对该资源有MANAGE权限
        engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, req.id(), OperationCodeConstants.MANAGE);

        if (req.code() != null) entity.setCode(req.code());
        if (req.name() != null) entity.setName(req.name());
        if (req.path() != null) entity.setPath(req.path());
        if (req.status() != null) entity.setStatus(req.status());
        if (req.sortOrder() != null) entity.setSortOrder(req.sortOrder());
        if (req.extra() != null) entity.setExtra(req.extra());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operatorId);
        resourceEntityMapper.update(entity);
        return toResourceResp(entity);
    }

    /**
     * 移动资源到新的父资源下
     * <p>
     * 调整资源在树结构中的位置。
     * 需要有对该资源的MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID
     * @param parentId   目标父资源ID，null表示移至根级
     * @param operatorId 操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveResource(Long tenantId, Long resourceId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, resourceId);
        if (entity == null) {
            throw new IllegalArgumentException("资源不存在: " + resourceId);
        }

        // 实例级权限检查
        engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationCodeConstants.MANAGE);

        if (parentId != null) {
            ResourceEntity parent = resourceEntityDomainService.selectValidById(tenantId, parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父资源不存在: " + parentId);
            }
        }
        entity.setParentId(parentId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        resourceEntityMapper.update(entity);
    }

    /**
     * 删除单个资源及其所有子孙资源
     * <p>
     * 软删除指定资源及其所有子孙资源。
     * 需要有对该资源的MANAGE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID
     * @param operatorId 操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long tenantId, Long resourceId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, resourceId);
        if (entity == null) {
            throw new IllegalArgumentException("资源不存在: " + resourceId);
        }

        // 实例级权限检查
        engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationCodeConstants.MANAGE);

        resourceEntityDomainService.deleteWithChildren(tenantId, resourceId);
    }

    /**
     * 批量删除资源及其所有子孙资源
     * <p>
     * 批量软删除资源及其所有子孙资源，同时清理关联的角色权限。
     * 使用批量查询优化避免N+1问题。
     * 仅删除有MANAGE权限的资源，无权限的资源会被跳过。
     * </p>
     *
     * @param tenantId    租户ID
     * @param resourceIds 待删除的资源ID列表
     * @param operatorId  操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResources(Long tenantId, List<Long> resourceIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validResourceIds = resourceIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validResourceIds.isEmpty()) {
            return;
        }

        // 批量查询资源验证存在性（1次查询，避免N+1）
        List<ResourceEntity> entities = resourceEntityDomainService.selectValidByIds(tenantId, validResourceIds);
        if (entities.isEmpty()) {
            return;
        }

        // 构建已存在资源ID集合
        Set<Long> existingResourceIds = entities.stream()
            .map(ResourceEntity::getId)
            .collect(Collectors.toSet());

        // 批量权限检查（1次查询，避免N+1）
        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.RESOURCE, existingResourceIds, OperationCodeConstants.MANAGE);

        // 过滤有权限删除的资源
        Set<Long> permittedIds = existingResourceIds.stream()
            .filter(id -> !deniedIds.contains(id))
            .collect(Collectors.toSet());

        if (permittedIds.isEmpty()) {
            log.info("操作者{}无权删除任何请求的资源: denied={}", operatorId, deniedIds.size());
            return;
        }

        // 批量获取所有子孙ID包括自身（1次CTE查询，避免N+1）
        Map<Long, List<Long>> descendantsMap = resourceEntityDomainService.batchGetDescendantIds(tenantId, permittedIds);

        // 收集所有待删除ID（包括子孙）
        Set<Long> allIdsToDelete = new HashSet<>(permittedIds);
        for (List<Long> descendants : descendantsMap.values()) {
            allIdsToDelete.addAll(descendants);
        }

        // 批量查询关联的角色权限（1次查询，避免N+1）
        List<Long> permIds = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(allIdsToDelete))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        ).stream().map(RoleResourcePermission::getId).toList();

        // 批量软删除所有资源（1次更新，避免N+1）
        LocalDateTime now = LocalDateTime.now();
        resourceEntityDomainService.softDeleteBatch(tenantId, new ArrayList<>(allIdsToDelete), now);

        // 批量软删除关联的角色权限（1次更新，避免N+1）
        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }

        // 记录操作日志
        operationLogDomainService.asyncRecord(
            "perm",
            "resource-entity-remove",
            "BATCH",
            tenantId,
            "软删除了" + allIdsToDelete.size() + "个资源（包括子孙），根节点=" + permittedIds.size() + "，拒绝=" + deniedIds.size(),
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 查询资源树结构
     * <p>
     * 返回指定资源类型和域下的资源层级树结构。
     * 用于前端展示资源组织关系。
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceTypeCode 资源类型编码，可选过滤条件
     * @param domainCode      域编码，可选过滤条件
     * @return 资源树结构列表
     */
    @Override
    public List<ResourceTreeResp> getResourceTree(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        QueryWrapper qw = QueryWrapper.create()
            .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.STATUS.eq(PermissionConstants.ENABLED_STATUS));
        if (resourceType != null) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        applyDomainFilter(qw, tenantId, domainCode);

        List<ResourceEntity> allEntities = resourceEntityMapper.selectListByQuery(qw);

        // 使用TreeBuilder构建树
        TreeBuilder<ResourceEntity, ResourceTreeNode> treeBuilder = new TreeBuilder<>(
            ResourceEntity::getId,
            ResourceEntity::getParentId,
            (entity, children) -> new ResourceTreeNode(
                entity.getId(), entity.getParentId(),
                typeResolutionService.resolveTypeCode(entity.getTenantId(), "resource_type", entity.getResourceType()),
                entity.getCode(), entity.getCodeType(), entity.getName(),
                entity.getPath(), entity.getStatus(), entity.getSortOrder(), children
            )
        );

        List<ResourceEntity> roots = allEntities.stream()
            .filter(r -> r.getParentId() == null)
            .collect(Collectors.toList());

        return treeBuilder.buildTrees(roots, allEntities).stream()
            .map(ResourceTreeResp::new)
            .collect(Collectors.toList());
    }

    /**
     * 分页查询资源列表
     * <p>
     * 支持按资源类型、域过滤，返回分页结果。
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceTypeCode 资源类型编码，可选
     * @param domainCode      域编码，可选
     * @param offset          偏移量
     * @param limit           每页数量
     * @return 资源列表
     */
    @Override
    public List<ResourceResp> listResources(Long tenantId, String resourceTypeCode, String domainCode, int offset, int limit) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        QueryWrapper qw = QueryWrapper.create()
            .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        applyDomainFilter(qw, tenantId, domainCode);
        qw.limit(limit).offset(offset);
        return resourceEntityMapper.selectListByQuery(qw)
            .stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    /**
     * 统计资源数量
     * <p>
     * 支持按资源类型、域过滤。
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceTypeCode 资源类型编码，可选
     * @param domainCode      域编码，可选
     * @return 资源总数
     */
    @Override
    public long countResources(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        QueryWrapper qw = QueryWrapper.create()
            .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        applyDomainFilter(qw, tenantId, domainCode);
        return resourceEntityMapper.selectCountByQuery(qw);
    }

    /**
     * 应用域过滤条件
     * <p>
     * 根据域编码使用DomainClassifyService按资源类型过滤域范围。
     * 替代原先直接按BIZ_DOMAIN_ID列过滤的方式。
     * </p>
     *
     * @param qw         查询包装器
     * @param tenantId   租户ID
     * @param domainCode 域编码
     */
    private void applyDomainFilter(QueryWrapper qw, Long tenantId, String domainCode) {
        if (domainCode != null && !domainCode.isBlank()) {
            DomainTypeFilter typeFilter = domainClassifyService.buildTypeFilter(
                tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode);
            applyResourceTypeFilter(qw, typeFilter);
        }
    }

    /**
     * 将资源类型过滤条件应用到查询中
     * <p>
     * GLOBAL_PLUS 语义是“指定域类型 OR 全局未认领类型”，
     * 不能拆成两个 and 条件，否则会把指定域类型错误过滤掉。
     * </p>
     *
     * @param qw         查询包装器
     * @param typeFilter 域类型过滤条件
     */
    private void applyResourceTypeFilter(QueryWrapper qw, DomainTypeFilter typeFilter) {
        if (typeFilter.isNoFilter()) {
            return;
        }
        if (typeFilter.isMatchNone()) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.ID.eq(PermissionConstants.NONEXISTENT_ID));
            return;
        }
        if (!typeFilter.getIncludeValues().isEmpty() && !typeFilter.getExcludeValues().isEmpty()) {
            qw.and(
                ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.in(typeFilter.getIncludeValues())
                    .or(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.notIn(typeFilter.getExcludeValues()))
            );
            return;
        }
        if (!typeFilter.getIncludeValues().isEmpty()) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.in(typeFilter.getIncludeValues()));
            return;
        }
        if (!typeFilter.getExcludeValues().isEmpty()) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.notIn(typeFilter.getExcludeValues()));
        }
    }

    /**
     * 添加API映射
     * <p>
     * 将资源实体与API接口建立映射关系。
     * 需要有对服务的MANAGE_API_MAPPING权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      API映射创建请求
     * @return 创建成功的映射详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req) {
        // 权限验证：检查对SERVICE资源的MANAGE_API_MAPPING权限
        Long operatorId = OperatorContext.getOperatorId();
        engine.validate(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.MANAGE_API_MAPPING);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.resourceId());
        if (entity == null) {
            throw new IllegalArgumentException("资源不存在: " + req.resourceId());
        }

        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setTenantId(tenantId);
        mapping.setResourceEntityId(req.resourceId());
        mapping.setServiceCode(req.serviceCode());
        mapping.setHttpMethod(req.httpMethod());
        mapping.setPathPattern(req.pathPattern());
        mapping.setMatchOrder(req.matchOrder());
        mapping.setEnabled(req.enabled() != null ? req.enabled() : true);
        mapping.setExtra(req.extra());
        LocalDateTime now = LocalDateTime.now();
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);
        mapping.setDeleteFlag(0L);
        apiMappingMapper.insert(mapping);
        return toApiMappingResp(mapping);
    }

    /**
     * 批量删除API映射
     * <p>
     * 批量软删除API映射关系。
     * 使用批量权限验证避免N+1问题。
     * </p>
     *
     * @param tenantId   租户ID
     * @param mappingIds 待删除的映射ID列表
     * @param operatorId 操作者ID，可选
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (mappingIds == null || mappingIds.isEmpty()) {
            return;
        }

        // 过滤null值ID
        Set<Long> validMappingIds = mappingIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validMappingIds.isEmpty()) {
            return;
        }

        // 批量查询映射获取其服务编码（避免N+1）
        List<ResourceApiMapping> mappings = resourceApiMappingDomainService.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.ID.in(validMappingIds))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );
        if (mappings.isEmpty()) {
            return;
        }

        // 收集所有唯一服务编码并批量验证权限
        Set<String> serviceCodes = mappings.stream()
            .map(ResourceApiMapping::getServiceCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());

        // 使用批量验证避免N+1查询
        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCodes, OperationCodeConstants.MANAGE_API_MAPPING);

        // 批量软删除所有有效映射（单次查询）
        LocalDateTime now = LocalDateTime.now();
        List<Long> mappingIdsToDelete = mappings.stream()
            .map(ResourceApiMapping::getId)
            .collect(Collectors.toList());
        int n = resourceApiMappingDomainService.softDeleteBatch(tenantId, mappingIdsToDelete, now);

        operationLogDomainService.asyncRecord(
            "perm",
            "resource-api-mapping-remove",
            "BATCH",
            tenantId,
            "软删除了" + n + "条resource_api_mapping记录，ids=" + mappingIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    /**
     * 查询API映射列表
     * <p>
     * 查询指定资源或服务的所有API映射关系。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID，可选过滤条件
     * @param serviceCode 服务编码，可选过滤条件
     * @return API映射列表
     */
    @Override
    public List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
            .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0));
        if (resourceId != null) {
            qw.and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.eq(resourceId));
        }
        if (serviceCode != null && !serviceCode.isBlank()) {
            qw.and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.SERVICE_CODE.eq(serviceCode));
        }
        return apiMappingMapper.selectListByQuery(qw)
            .stream().map(this::toApiMappingResp).collect(Collectors.toList());
    }

    /**
     * 更新API映射
     * <p>
     * 更新映射的HTTP方法、路径、启用状态等属性。
     * 需要有对服务的MANAGE_API_MAPPING权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      API映射更新请求
     * @return 更新后的映射详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req) {
        // 权限验证：检查对映射所属服务的MANAGE_API_MAPPING权限
        Long operatorId = OperatorContext.getOperatorId();

        ResourceApiMapping mapping = resourceApiMappingDomainService.selectValidById(tenantId, req.mappingId());
        if (mapping == null || !Objects.equals(mapping.getResourceEntityId(), req.resourceId())) {
            throw new IllegalArgumentException("API映射不存在: " + req.mappingId());
        }

        // 使用映射的服务编码验证权限（从数据库获取，而非请求）
        engine.validate(tenantId, operatorId, ResourceTypeCode.SERVICE, mapping.getServiceCode(), OperationCodeConstants.MANAGE_API_MAPPING);

        if (req.httpMethod() != null) {
            mapping.setHttpMethod(req.httpMethod());
        }
        if (req.pathPattern() != null) {
            mapping.setPathPattern(req.pathPattern());
        }
        if (req.matchOrder() != null) {
            mapping.setMatchOrder(req.matchOrder());
        }
        if (req.enabled() != null) {
            mapping.setEnabled(req.enabled());
        }
        if (req.extra() != null) {
            mapping.setExtra(req.extra());
        }
        mapping.setUpdatedAt(LocalDateTime.now());
        apiMappingMapper.update(mapping);

        // 安全修复：添加租户ID条件
        ResourceApiMapping updated = apiMappingMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.ID.eq(req.mappingId()))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );
        return toApiMappingResp(updated);
    }

    /**
     * 将资源实体转换为响应对象
     *
     * @param entity 资源实体
     * @return 资源响应对象
     */
    private ResourceResp toResourceResp(ResourceEntity entity) {
        String resourceTypeName = ResourceType.safeGetLabel(entity.getResourceType());
        return new ResourceResp(
            entity.getId(), entity.getTenantId(),
            entity.getParentId(), typeResolutionService.resolveTypeCode(entity.getTenantId(), "resource_type", entity.getResourceType()), resourceTypeName,
            entity.getCode(), entity.getCodeType(), entity.getName(),
            entity.getPath(), entity.getStatus(), entity.getSortOrder(),
            entity.getExtra(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    /**
     * 将API映射转换为响应对象
     *
     * @param mapping API映射实体
     * @return API映射响应对象
     */
    private ApiMappingResp toApiMappingResp(ResourceApiMapping mapping) {
        return new ApiMappingResp(
            mapping.getId(), mapping.getTenantId(),
            mapping.getResourceEntityId(), mapping.getServiceCode(),
            mapping.getHttpMethod(), mapping.getPathPattern(),
            mapping.getMatchOrder(), mapping.getEnabled(),
            mapping.getExtra(), mapping.getCreatedAt(), mapping.getUpdatedAt()
        );
    }
}