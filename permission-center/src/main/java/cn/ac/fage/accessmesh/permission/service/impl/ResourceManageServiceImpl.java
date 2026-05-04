package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp.ResourceTreeNode;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.enums.ResourceType;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceApiMappingDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;

@Service
public class ResourceManageServiceImpl implements ResourceManageService {

    private static final Logger log = LoggerFactory.getLogger(ResourceManageServiceImpl.class);

    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final ResourceApiMappingDomainService resourceApiMappingDomainService;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;
    private final AuthorizationService authorizationService;
    private final ResourcePermissionValidator permissionValidator;

    public ResourceManageServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                     ResourceApiMappingMapper apiMappingMapper,
                                     ResourceEntityDomainService resourceEntityDomainService,
                                     ResourceApiMappingDomainService resourceApiMappingDomainService,
                                     TypeResolutionService typeResolutionService,
                                     OperationLogDomainService operationLogDomainService,
                                     AuthorizationService authorizationService,
                                     ResourcePermissionValidator permissionValidator) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.resourceApiMappingDomainService = resourceApiMappingDomainService;
        this.typeResolutionService = typeResolutionService;
        this.operationLogDomainService = operationLogDomainService;
        this.authorizationService = authorizationService;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        // Permission check
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationType.CREATE)) {
            throw new SecurityException("No permission to create resource");
        }

        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(tenantId);
        entity.setBizDomainId(req.bizDomainId());
        entity.setParentId(req.parentId());
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", req.resourceTypeCode());
        if (resourceType == null) {
            throw new IllegalArgumentException("Unknown resourceTypeCode: " + req.resourceTypeCode());
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

        // Permission check
        if (!permissionValidator.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationType.CREATE)) {
            throw new SecurityException("No permission to create resources");
        }

        List<ResourceResp> created = new ArrayList<>();
        for (ResourceCreateReq req : reqs) {
            created.add(createResource(tenantId, req, operatorId));
        }
        return created;
    }

    @Override
    public ResourceResp getResource(Long tenantId, Long resourceId) {
        ResourceEntity entity = resourceEntityMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.ID.eq(resourceId))
                .and(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        return entity != null ? toResourceResp(entity) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.id());
        if (entity == null) {
            throw new IllegalArgumentException("Resource not found: " + req.id());
        }

        // Instance-level permission check: operator must have MANAGE permission on this specific resource
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, req.id(), OperationType.MANAGE);

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
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }

        // Instance-level permission check: operator must have MANAGE permission on this specific resource
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationType.MANAGE);

        if (parentId != null) {
            ResourceEntity parent = resourceEntityDomainService.selectValidById(tenantId, parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Parent resource not found: " + parentId);
            }
        }
        entity.setParentId(parentId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        resourceEntityMapper.update(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResource(Long tenantId, Long resourceId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, resourceId);
        if (entity == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }

        // Instance-level permission check: operator must have MANAGE permission on this specific resource
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationType.MANAGE);

        resourceEntityDomainService.deleteWithChildren(tenantId, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResources(Long tenantId, List<Long> resourceIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }

        // Filter out null IDs
        Set<Long> validResourceIds = resourceIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validResourceIds.isEmpty()) {
            return;
        }

        // Batch query resources to validate existence
        List<ResourceEntity> entities = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.ID.in(validResourceIds))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );

        if (entities.isEmpty()) {
            return;
        }

        // Build map of existing resources
        Map<Long, ResourceEntity> existingResources = entities.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // Batch permission check - avoid N+1 queries
        Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, ResourceTypeCode.RESOURCE, existingResources.keySet(), OperationType.MANAGE);

        // Filter resources that operator has permission to delete
        List<Long> permittedIds = new ArrayList<>();
        for (Long resourceId : existingResources.keySet()) {
            if (!deniedIds.contains(resourceId)) {
                permittedIds.add(resourceId);
            } else {
                log.info("Operator {} denied to delete resource: {}", operatorId, resourceId);
            }
        }

        // Execute batch delete for permitted resources
        for (Long resourceId : permittedIds) {
            resourceEntityDomainService.deleteWithChildren(tenantId, resourceId);
        }

        if (!permittedIds.isEmpty()) {
            operationLogDomainService.asyncRecord(
                "perm",
                "resource-entity-remove",
                "BATCH",
                tenantId,
                "soft-deleted " + permittedIds.size() + " resource(s), ids=" + permittedIds + ", denied=" + deniedIds.size(),
                operatorId,
                null,
                null,
                tenantId
            );
        }
    }

    @Override
    public List<ResourceTreeResp> getResourceTree(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            .and(RESOURCE_ENTITY.STATUS.eq(PermissionConstants.ENABLED_STATUS));
        if (resourceType != null) {
            qw.and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        if (domainCode != null && !domainCode.isBlank()) {
            Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (bizDomainId == null) {
                return List.of();
            }
            qw.and(RESOURCE_ENTITY.BIZ_DOMAIN_ID.eq(bizDomainId).or(RESOURCE_ENTITY.BIZ_DOMAIN_ID.isNull()));
        } else {
            qw.and(RESOURCE_ENTITY.BIZ_DOMAIN_ID.isNull());
        }

        List<ResourceEntity> allEntities = resourceEntityMapper.selectListByQuery(qw);

        // Build tree using TreeBuilder
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
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        applyDomainFilter(qw, tenantId, domainCode);
        qw.limit(limit).offset(offset);

        return resourceEntityMapper.selectListByQuery(qw)
            .stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    @Override
    public long countResources(Long tenantId, String resourceTypeCode, String domainCode) {
        Integer resourceType = null;
        if (resourceTypeCode != null && !resourceTypeCode.isBlank()) {
            resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        applyDomainFilter(qw, tenantId, domainCode);
        return resourceEntityMapper.selectCountByQuery(qw);
    }

    private void applyDomainFilter(QueryWrapper qw, Long tenantId, String domainCode) {
        if (domainCode != null && !domainCode.isBlank()) {
            Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (bizDomainId != null) {
                qw.and(RESOURCE_ENTITY.BIZ_DOMAIN_ID.eq(bizDomainId).or(RESOURCE_ENTITY.BIZ_DOMAIN_ID.isNull()));
            } else {
                qw.and(RESOURCE_ENTITY.BIZ_DOMAIN_ID.isNull());
            }
        } else {
            qw.and(RESOURCE_ENTITY.BIZ_DOMAIN_ID.isNull());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req) {
        // Permission validation: check MANAGE_API_MAPPING permission on SERVICE resource
        Long operatorId = OperatorContext.getOperatorId();
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationType.MANAGE_API_MAPPING);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.resourceId());
        if (entity == null) {
            throw new IllegalArgumentException("Resource not found: " + req.resourceId());
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

        // Filter out null IDs and collect valid IDs
        Set<Long> validMappingIds = mappingIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validMappingIds.isEmpty()) {
            return;
        }

        // Batch query mappings to get their service codes (avoid N+1)
        List<ResourceApiMapping> mappings = resourceApiMappingDomainService.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(RESOURCE_API_MAPPING.ID.in(validMappingIds))
                .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );

        if (mappings.isEmpty()) {
            return;
        }

        // Collect all unique service codes and validate permission in batch
        Set<String> serviceCodes = mappings.stream()
            .map(ResourceApiMapping::getServiceCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());

        // Use batch validation to avoid N+1 queries
        permissionValidator.validateBatch(tenantId, operatorId, ResourceTypeCode.SERVICE, serviceCodes, OperationType.MANAGE_API_MAPPING);

        // Batch soft delete all valid mappings (single query)
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
            "soft-deleted " + n + " resource_api_mapping row(s), ids=" + mappingIds,
            operatorId,
            null,
            null,
            tenantId
        );
    }

    @Override
    public List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
            .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0));
        if (resourceId != null) {
            qw.and(RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.eq(resourceId));
        }
        if (serviceCode != null && !serviceCode.isBlank()) {
            qw.and(RESOURCE_API_MAPPING.SERVICE_CODE.eq(serviceCode));
        }
        return apiMappingMapper.selectListByQuery(qw)
            .stream().map(this::toApiMappingResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req) {
        // Permission validation: check MANAGE_API_MAPPING permission on the mapping's service
        Long operatorId = OperatorContext.getOperatorId();
        ResourceApiMapping mapping = resourceApiMappingDomainService.selectValidById(tenantId, req.mappingId());
        if (mapping == null || !Objects.equals(mapping.getResourceEntityId(), req.resourceId())) {
            throw new IllegalArgumentException("Api mapping not found: " + req.mappingId());
        }
        // Validate permission using the mapping's service code (from database, not request)
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.SERVICE, mapping.getServiceCode(), OperationType.MANAGE_API_MAPPING);
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
        ResourceApiMapping updated = apiMappingMapper.selectOneById(req.mappingId());
        return toApiMappingResp(updated);
    }

    private ResourceResp toResourceResp(ResourceEntity entity) {
        String resourceTypeName = ResourceType.safeGetLabel(entity.getResourceType());

        return new ResourceResp(
            entity.getId(), entity.getTenantId(), entity.getBizDomainId(),
            entity.getParentId(), typeResolutionService.resolveTypeCode(entity.getTenantId(), "resource_type", entity.getResourceType()), resourceTypeName,
            entity.getCode(), entity.getCodeType(), entity.getName(),
            entity.getPath(), entity.getStatus(), entity.getSortOrder(),
            entity.getExtra(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private ApiMappingResp toApiMappingResp(ResourceApiMapping mapping) {
        return new ApiMappingResp(
            mapping.getId(), mapping.getTenantId(), mapping.getBizDomainId(),
            mapping.getResourceEntityId(), mapping.getServiceCode(),
            mapping.getHttpMethod(), mapping.getPathPattern(),
            mapping.getMatchOrder(), mapping.getEnabled(),
            mapping.getExtra(), mapping.getCreatedAt(), mapping.getUpdatedAt()
        );
    }
}
