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
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;

@Service
public class ResourceManageServiceImpl implements ResourceManageService {

    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;
    private final AuthorizationService authorizationService;

    public ResourceManageServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                     ResourceApiMappingMapper apiMappingMapper,
                                     ResourceEntityDomainService resourceEntityDomainService,
                                     TypeResolutionService typeResolutionService,
                                     OperationLogDomainService operationLogDomainService,
                                     AuthorizationService authorizationService) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.typeResolutionService = typeResolutionService;
        this.operationLogDomainService = operationLogDomainService;
        this.authorizationService = authorizationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "RESOURCE", "CREATE")) {
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
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleteFlag(0L);
        resourceEntityMapper.insert(entity);
        return toResourceResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "RESOURCE", "CREATE")) {
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
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "RESOURCE", "UPDATE")) {
            throw new SecurityException("No permission to update resource");
        }

        ResourceEntity entity = resourceEntityMapper.selectOneById(req.id());
        if (entity == null || entity.getDeleteFlag() != 0L || !entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found: " + req.id());
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
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "RESOURCE", "UPDATE")) {
            throw new SecurityException("No permission to move resource");
        }

        ResourceEntity entity = resourceEntityMapper.selectOneById(resourceId);
        if (entity == null || entity.getDeleteFlag() != 0L || !tenantId.equals(entity.getTenantId())) {
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }
        if (parentId != null) {
            ResourceEntity parent = resourceEntityMapper.selectOneById(parentId);
            if (parent == null || parent.getDeleteFlag() != 0L || !tenantId.equals(parent.getTenantId())) {
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
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "RESOURCE", "DELETE")) {
            throw new SecurityException("No permission to delete resource");
        }

        ResourceEntity entity = resourceEntityMapper.selectOneById(resourceId);
        if (entity == null || entity.getDeleteFlag() != 0L || !entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }

        resourceEntityDomainService.deleteWithChildren(tenantId, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResources(Long tenantId, List<Long> resourceIds, Long operatorId) {
        // Resolve operatorId from context if not provided
        if (operatorId == null) {
            operatorId = OperatorContext.getOperatorId();
        }

        // Permission check
        if (!authorizationService.hasPermission(tenantId, operatorId, "RESOURCE", "DELETE")) {
            throw new SecurityException("No permission to delete resources");
        }

        for (Long resourceId : resourceIds) {
            deleteResource(tenantId, resourceId, operatorId);
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
            .and(RESOURCE_ENTITY.STATUS.eq(1));
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

        List<ResourceEntity> roots = allEntities.stream()
            .filter(r -> r.getParentId() == null)
            .collect(Collectors.toList());

        List<ResourceTreeResp> result = new ArrayList<>();
        for (ResourceEntity root : roots) {
            result.add(new ResourceTreeResp(buildTreeNode(root, allEntities)));
        }
        return result;
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
        ResourceEntity entity = resourceEntityMapper.selectOneById(req.resourceId());
        if (entity == null || entity.getDeleteFlag() != 0L || !entity.getTenantId().equals(tenantId)) {
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
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        mapping.setDeleteFlag(0L);
        apiMappingMapper.insert(mapping);
        return toApiMappingResp(mapping);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId) {
        if (mappingIds == null || mappingIds.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long mappingId : mappingIds) {
            if (mappingId == null) {
                continue;
            }
            ResourceApiMapping mapping = apiMappingMapper.selectOneById(mappingId);
            if (mapping != null && mapping.getDeleteFlag() == 0L
                && Objects.equals(tenantId, mapping.getTenantId())) {
                mapping.setDeleteFlag(mapping.getId());
                mapping.setDeletedAt(LocalDateTime.now());
                apiMappingMapper.update(mapping);
                n++;
            }
        }
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
        ResourceApiMapping mapping = apiMappingMapper.selectOneById(req.mappingId());
        if (mapping == null || mapping.getDeleteFlag() != 0L
            || !mapping.getResourceEntityId().equals(req.resourceId())
            || !mapping.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Api mapping not found: " + req.mappingId());
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
        ResourceApiMapping updated = apiMappingMapper.selectOneById(req.mappingId());
        return toApiMappingResp(updated);
    }

    private ResourceTreeNode buildTreeNode(ResourceEntity entity, List<ResourceEntity> allEntities) {
        List<ResourceTreeNode> children = allEntities.stream()
            .filter(r -> entity.getId().equals(r.getParentId()))
            .map(r -> buildTreeNode(r, allEntities))
            .collect(Collectors.toList());

        return new ResourceTreeNode(
            entity.getId(), entity.getParentId(), typeResolutionService.resolveTypeCode(entity.getTenantId(), "resource_type", entity.getResourceType()),
            entity.getCode(), entity.getCodeType(), entity.getName(),
            entity.getPath(), entity.getStatus(), entity.getSortOrder(), children
        );
    }

    private ResourceResp toResourceResp(ResourceEntity entity) {
        String resourceTypeName = "";
        try {
            resourceTypeName = ResourceType.fromValue(entity.getResourceType() != null ? entity.getResourceType() : 0).getLabel();
        } catch (IllegalArgumentException ignored) {}

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
