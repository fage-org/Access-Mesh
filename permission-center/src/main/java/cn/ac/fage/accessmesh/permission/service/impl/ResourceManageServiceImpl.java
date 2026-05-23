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

import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;

import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;

import cn.ac.fage.accessmesh.permission.util.OperatorContext;

import cn.ac.fage.accessmesh.permission.util.OperatorUtil;

import cn.ac.fage.accessmesh.permission.util.PermissionConstants;

import cn.ac.fage.accessmesh.permission.util.TreeBuilder;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无批量创建资源的权限");
        }

        if (reqs == null || reqs.isEmpty()) {
            return List.of();
        }

        Set<Long> allParentIds = reqs.stream()
            .map(ResourceCreateReq::parentId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());
        Set<String> allCodes = reqs.stream()
            .map(ResourceCreateReq::code)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());

        Map<Long, ResourceEntity> parentMap = resourceEntityDomainService.batchSelectByIdsMap(tenantId, allParentIds);
        Set<String> existingCodes = resourceEntityDomainService.findExistingCodes(tenantId, allCodes);

        Set<String> typeCodes = reqs.stream()
            .map(ResourceCreateReq::resourceTypeCode)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> typeValueMap = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", typeCodes);

        LocalDateTime now = LocalDateTime.now();
        List<ResourceEntity> toInsert = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < reqs.size(); i++) {
            ResourceCreateReq req = reqs.get(i);

            if (req.parentId() != null && req.parentId() > 0 && !parentMap.containsKey(req.parentId())) {
                errors.add("req[" + i + "]: 父资源不存在: " + req.parentId());
                continue;
            }

            if (req.code() != null && !req.code().isBlank() && existingCodes.contains(req.code())) {
                errors.add("req[" + i + "]: 编码已存在: " + req.code());
                continue;
            }

            Integer resourceType = typeValueMap.get(req.resourceTypeCode());
            if (resourceType == null) {
                errors.add("req[" + i + "]: 未知的resourceTypeCode: " + req.resourceTypeCode());
                continue;
            }

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

        if (!errors.isEmpty()) {
            log.warn("批量创建资源验证错误: {}", errors);
        }

        if (!toInsert.isEmpty()) {
            resourceEntityMapper.insertBatch(toInsert);
        }

        return toInsert.stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    @Override
    public ResourceResp getResource(Long tenantId, Long resourceId) {
        ResourceEntity entity = resourceEntityMapper.selectValidById(resourceId, tenantId);
        return entity != null ? toResourceResp(entity) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.id());
        if (entity == null) {
            throw new IllegalArgumentException("资源不存在: " + req.id());
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, req.id(), OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on RESOURCE:" + req.id());
        }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveResource(Long tenantId, Long resourceId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, resourceId);
        if (entity == null) {
            throw new IllegalArgumentException("资源不存在: " + resourceId);
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on RESOURCE:" + resourceId);
        }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResources(Long tenantId, List<Long> resourceIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }

        Set<Long> validResourceIds = resourceIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validResourceIds.isEmpty()) {
            return;
        }

        List<ResourceEntity> entities = resourceEntityDomainService.selectValidByIds(tenantId, validResourceIds);
        if (entities.isEmpty()) {
            return;
        }

        Set<Long> existingResourceIds = entities.stream()
            .map(ResourceEntity::getId)
            .collect(Collectors.toSet());

        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.RESOURCE, existingResourceIds, OperationCodeConstants.MANAGE);

        Set<Long> permittedIds = existingResourceIds.stream()
            .filter(id -> !deniedIds.contains(id))
            .collect(Collectors.toSet());

        if (permittedIds.isEmpty()) {
            log.info("操作者{}无权删除任何请求的资源: denied={}", operatorId, deniedIds.size());
            return;
        }

        Map<Long, List<Long>> descendantsMap = resourceEntityDomainService.batchGetDescendantIds(tenantId, permittedIds);

        Set<Long> allIdsToDelete = new HashSet<>(permittedIds);
        for (List<Long> descendants : descendantsMap.values()) {
            allIdsToDelete.addAll(descendants);
        }

        List<Long> permIds = rolePermMapper.selectValidPermIdsByResourceIds(tenantId, new ArrayList<>(allIdsToDelete));

        LocalDateTime now = LocalDateTime.now();
        resourceEntityDomainService.softDeleteBatch(tenantId, new ArrayList<>(allIdsToDelete), now);

        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }

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

    @Override
    public List<ResourceTreeResp> getResourceTree(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.RESOURCE);
        }

        List<ResourceEntity> allEntities = resourceEntityMapper.selectResourceTree(tenantId, resourceType, matchNone);

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

    @Override
    public List<ResourceResp> listResources(Long tenantId, String resourceTypeCode, String domainCode, int offset, int limit) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.RESOURCE);
        }

        return resourceEntityMapper.selectResourceListPaged(tenantId, resourceType, matchNone, offset, limit)
            .stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    @Override
    public long countResources(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }

        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.RESOURCE);
        }

        return resourceEntityMapper.selectResourceListCount(tenantId, resourceType, matchNone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.MANAGE_API_MAPPING)) {
            throw new SecurityException("Permission denied: MANAGE_API_MAPPING on SERVICE:" + req.serviceCode());
        }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (mappingIds == null || mappingIds.isEmpty()) {
            return;
        }

        Set<Long> validMappingIds = mappingIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validMappingIds.isEmpty()) {
            return;
        }

        List<ResourceApiMapping> mappings = resourceApiMappingDomainService.selectValidByIds(tenantId, validMappingIds);
        if (mappings.isEmpty()) {
            return;
        }

        Set<String> serviceCodes = mappings.stream()
            .map(ResourceApiMapping::getServiceCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());

        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCodes, OperationCodeConstants.MANAGE_API_MAPPING);

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

    @Override
    public List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode) {
        return apiMappingMapper.selectValidList(tenantId, resourceId, serviceCode)
            .stream().map(this::toApiMappingResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        ResourceApiMapping mapping = resourceApiMappingDomainService.selectValidById(tenantId, req.mappingId());
        if (mapping == null || !Objects.equals(mapping.getResourceEntityId(), req.resourceId())) {
            throw new IllegalArgumentException("API映射不存在: " + req.mappingId());
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, mapping.getServiceCode(), OperationCodeConstants.MANAGE_API_MAPPING)) {
            throw new SecurityException("Permission denied: MANAGE_API_MAPPING on SERVICE:" + mapping.getServiceCode());
        }

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

        ResourceApiMapping updated = apiMappingMapper.selectValidById(req.mappingId(), tenantId);
        return toApiMappingResp(updated);
    }

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