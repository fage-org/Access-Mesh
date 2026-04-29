package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingReq;
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
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;

@Service
public class ResourceManageServiceImpl implements ResourceManageService {

    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final ResourceEntityDomainService resourceEntityDomainService;

    public ResourceManageServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                     ResourceApiMappingMapper apiMappingMapper,
                                     ResourceEntityDomainService resourceEntityDomainService) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
    }

    @Override
    @Transactional
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(tenantId);
        entity.setBizDomainId(req.bizDomainId());
        entity.setParentId(req.parentId());
        entity.setResourceType(req.resourceType());
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
    @Transactional
    public ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId) {
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
    @Transactional
    public void deleteResource(Long tenantId, Long resourceId, Long operatorId) {
        ResourceEntity entity = resourceEntityMapper.selectOneById(resourceId);
        if (entity == null || entity.getDeleteFlag() != 0L || !entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }

        resourceEntityDomainService.deleteWithChildren(tenantId, resourceId);
    }

    @Override
    public List<ResourceTreeResp> getResourceTree(Long tenantId, Integer resourceType) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            .and(RESOURCE_ENTITY.STATUS.eq(1));
        if (resourceType != null) {
            qw.and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
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
    public List<ResourceResp> listResources(Long tenantId, Integer resourceType, int offset, int limit) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType));
        }
        qw.limit(limit).offset(offset);

        return resourceEntityMapper.selectListByQuery(qw)
            .stream().map(this::toResourceResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addApiMapping(Long tenantId, Long resourceId, ApiMappingReq req) {
        ResourceEntity entity = resourceEntityMapper.selectOneById(resourceId);
        if (entity == null || entity.getDeleteFlag() != 0L || !entity.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found: " + resourceId);
        }

        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setTenantId(tenantId);
        mapping.setResourceEntityId(resourceId);
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
    }

    @Override
    @Transactional
    public void removeApiMapping(Long tenantId, Long resourceId, Long mappingId, Long operatorId) {
        ResourceApiMapping mapping = apiMappingMapper.selectOneById(mappingId);
        if (mapping != null && mapping.getDeleteFlag() == 0L
            && mapping.getResourceEntityId().equals(resourceId)
            && mapping.getTenantId().equals(tenantId)) {
            mapping.setDeleteFlag(mapping.getId());
            mapping.setDeletedAt(LocalDateTime.now());
            apiMappingMapper.update(mapping);
        }
    }

    @Override
    public List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId) {
        return apiMappingMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.eq(resourceId))
                .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        ).stream().map(this::toApiMappingResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateApiMapping(Long tenantId, Long resourceId, Long mappingId, ApiMappingReq req) {
        ResourceApiMapping mapping = apiMappingMapper.selectOneById(mappingId);
        if (mapping != null && mapping.getDeleteFlag() == 0L
            && mapping.getResourceEntityId().equals(resourceId)
            && mapping.getTenantId().equals(tenantId)) {
            if (req.serviceCode() != null) mapping.setServiceCode(req.serviceCode());
            if (req.httpMethod() != null) mapping.setHttpMethod(req.httpMethod());
            if (req.pathPattern() != null) mapping.setPathPattern(req.pathPattern());
            if (req.matchOrder() != null) mapping.setMatchOrder(req.matchOrder());
            if (req.enabled() != null) mapping.setEnabled(req.enabled());
            if (req.extra() != null) mapping.setExtra(req.extra());
            mapping.setUpdatedAt(LocalDateTime.now());
            apiMappingMapper.update(mapping);
        }
    }

    private ResourceTreeNode buildTreeNode(ResourceEntity entity, List<ResourceEntity> allEntities) {
        List<ResourceTreeNode> children = allEntities.stream()
            .filter(r -> entity.getId().equals(r.getParentId()))
            .map(r -> buildTreeNode(r, allEntities))
            .collect(Collectors.toList());

        return new ResourceTreeNode(
            entity.getId(), entity.getParentId(), entity.getResourceType(),
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
            entity.getParentId(), entity.getResourceType(), resourceTypeName,
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
