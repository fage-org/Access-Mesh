package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.aop.PermissionChange;
import cn.ac.fage.accessmesh.access.permission.cache.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApiMappingUpdateReq;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ApiMappingResp;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceResp;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceTreeResp;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceTreeResp.ResourceTreeNode;

import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;

import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;

import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;

import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceType;

import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;

import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;

import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;

import cn.ac.fage.accessmesh.access.permission.service.ResourceManageAppService;

import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;

import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;

import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;

import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;

import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;

import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;

import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;

import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;

import cn.ac.fage.accessmesh.access.permission.util.TreeBuilder;

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
public class ResourceManageAppServiceImpl implements ResourceManageAppService {

    private static final Logger log = LoggerFactory.getLogger(ResourceManageAppServiceImpl.class);

    private final ResourceEntityMapper resourceEntityMapper;

    private final ResourceApiMappingMapper apiMappingMapper;

    private final ResourceEntityDomainService resourceEntityDomainService;

    private final TypeResolutionService typeResolutionService;

    private final DomainClassifyService domainClassifyService;

    private final PermQueryEngine engine;

    private final RoleResourcePermissionMapper rolePermMapper;

    /**
     * 构造函数注入依赖
     *
     * @param resourceEntityMapper        资源实体数据访问层
     * @param apiMappingMapper            资源API映射数据访问层
     * @param resourceEntityDomainService 资源实体领域服务
     * @param typeResolutionService       类型解析服务
     * @param domainClassifyService       域分类服务
     * @param engine                      权限查询引擎
     * @param rolePermMapper              角色资源权限数据访问层
     */
    public ResourceManageAppServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                     ResourceApiMappingMapper apiMappingMapper,
                                     ResourceEntityDomainService resourceEntityDomainService,
                                     TypeResolutionService typeResolutionService,
                                     DomainClassifyService domainClassifyService,
                                     PermQueryEngine engine,
                                     RoleResourcePermissionMapper rolePermMapper) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.engine = engine;
        this.rolePermMapper = rolePermMapper;
    }

    /**
     * 创建资源实体
     * <p>
     * 创建新的资源实体。资源实体是权限系统中的受保护对象。
     * 支持层级结构，可指定父资源。需要RESOURCE_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含资源编码、名称、类型等
     * @param operatorId 操作者ID，可选
     * @return 创建的资源响应
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "resource-entity-create", targetType = "resource_entity", targetId = "#result.id()", summary = "'create resource ' + #req.code()")
    public ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无创建资源的权限");
        }

        Long parentId = resolveParentId(tenantId, req);

        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(tenantId);
        entity.setParentId(parentId);
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", req.resourceTypeCode());
        if (resourceType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "未知的resourceTypeCode: " + req.resourceTypeCode());
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
    @OperationLog(module = "perm", action = "resource-entity-batch-create", targetType = "BATCH", targetId = "", summary = "'batch create resources, created=' + #result.size()")
    public List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("无批量创建资源的权限");
        }

        if (reqs == null || reqs.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return List.of();
        }

        Set<Long> allParentIds = reqs.stream()
            .filter(req -> !hasParentBusinessKey(req))
            .map(req -> normalizeParentId(req.parentId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<String> allCodes = reqs.stream()
            .map(ResourceCreateReq::code)
            .filter(c -> c != null && !c.isBlank())
            .collect(Collectors.toSet());

        Map<Long, ResourceEntity> parentMap = resourceEntityDomainService.batchSelectByIdsMap(tenantId, allParentIds);
        Set<String> existingCodes = resourceEntityDomainService.findExistingCodes(tenantId, allCodes);
        List<ResourceResolveRequest> parentResolveRequests = reqs.stream()
            .filter(this::hasParentBusinessKey)
            .map(this::toParentResolveRequest)
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> parentIdByKey = typeResolutionService.batchResolveResourceIds(tenantId, parentResolveRequests);

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

            Long parentId;
            if (hasParentBusinessKey(req)) {
                ResourceResolveRequest parentReq = toParentResolveRequest(req);
                parentId = parentIdByKey.get(parentReq.toKey());
                if (parentId == null) {
                    errors.add("req[" + i + "]: parent resource not found: " + parentKeyText(parentReq));
                    continue;
                }
            } else {
                parentId = normalizeParentId(req.parentId());
            }

            if (!hasParentBusinessKey(req) && req.parentId() != null && req.parentId() > 0 && !parentMap.containsKey(req.parentId())) {
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
            entity.setParentId(parentId);
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
        } else {
            OperationLogRuntimeContext.markSkip();
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
    @OperationLog(module = "perm", action = "resource-entity-update", targetType = "resource_entity", targetId = "#req.id()", summary = "'update resource ' + #req.id()")
    public ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.id());
        if (entity == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在: " + req.id());
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
    @OperationLog(module = "perm", action = "resource-entity-move", targetType = "resource_entity", targetId = "#resourceId", summary = "'move resource ' + #resourceId + ' to ' + #parentId")
    public void moveResource(Long tenantId, Long resourceId, Long parentId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, resourceId);
        if (entity == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在: " + resourceId);
        }

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on RESOURCE:" + resourceId);
        }

        if (parentId != null) {
            ResourceEntity parent = resourceEntityDomainService.selectValidById(tenantId, parentId);
            if (parent == null) {
                throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "父资源不存在: " + parentId);
            }
        }
        entity.setParentId(parentId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        resourceEntityMapper.update(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "perm", action = "resource-entity-remove", targetType = "BATCH", targetId = "", summary = "'batch remove resources'")
    public void deleteResources(Long tenantId, List<Long> resourceIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (resourceIds == null || resourceIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validResourceIds = resourceIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validResourceIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<ResourceEntity> entities = resourceEntityDomainService.selectValidByIds(tenantId, validResourceIds);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
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
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Map<Long, List<Long>> descendantsMap = resourceEntityDomainService.batchGetDescendantIds(tenantId, permittedIds);

        Set<Long> allIdsToDelete = new HashSet<>(permittedIds);
        for (List<Long> descendants : descendantsMap.values()) {
            allIdsToDelete.addAll(descendants);
        }

        // T-PERM-018 (C9)：软删 perm 前登记受影响范围。资源软删会级联软删 role_resource_permission，
        // ROLE_PERM_SNAPSHOT 缓存含旧 perm → 软删前查受影响 roleIds + serviceCodes 双重登记，afterCommit AOP 失效与广播
        List<Long> resourceIdsToDelete = new ArrayList<>(allIdsToDelete);
        Set<Long> affectedRoleIds = rolePermMapper.selectRoleIdsByResourceIds(tenantId, resourceIdsToDelete);
        if (!affectedRoleIds.isEmpty()) {
            PermissionChangeContext.markRoles(tenantId, affectedRoleIds);
        }
        // 资源 → API mapping → serviceCode：删除资源影响 Gateway 本地快照构建，软删前查出并 markServiceCodes
        Set<String> affectedServiceCodes = apiMappingMapper.selectByResourceEntityIds(tenantId, allIdsToDelete).stream()
            .map(ResourceApiMapping::getServiceCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        if (!affectedServiceCodes.isEmpty()) {
            PermissionChangeContext.markServiceCodes(tenantId, affectedServiceCodes);
        }

        List<Long> permIds = rolePermMapper.selectValidPermIdsByResourceIds(tenantId, resourceIdsToDelete);

        LocalDateTime now = LocalDateTime.now();
        resourceEntityDomainService.softDeleteBatch(tenantId, resourceIdsToDelete, now);

        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }

        OperationLogRuntimeContext.setSummary(
            "soft-deleted " + allIdsToDelete.size() + " resource(s), rootPermitted="
                + permittedIds.size() + ", denied=" + deniedIds.size()
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
    @PermissionChange
    @OperationLog(module = "perm", action = "resource-api-mapping-add", targetType = "resource_api_mapping", targetId = "#result.id()", summary = "'add api mapping for service ' + #req.serviceCode()")
    public ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SERVICE, req.serviceCode(), OperationCodeConstants.MANAGE_API_MAPPING)) {
            throw new SecurityException("Permission denied: MANAGE_API_MAPPING on SERVICE:" + req.serviceCode());
        }

        ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.resourceId());
        if (entity == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在: " + req.resourceId());
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
        // API mapping 变更不影响 ROLE_PERM_SNAPSHOT（perm 记录未变），仅影响 Gateway 本地快照 → 广播 serviceCodes
        PermissionChangeContext.markServiceCodes(tenantId, req.serviceCode());
        return toApiMappingResp(mapping);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "perm", action = "resource-api-mapping-remove", targetType = "BATCH", targetId = "", summary = "'batch remove resource api mappings'")
    public void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (mappingIds == null || mappingIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validMappingIds = mappingIds.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (validMappingIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<ResourceApiMapping> mappings = apiMappingMapper.selectValidByIds(tenantId, validMappingIds);
        if (mappings.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
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
        int n = apiMappingMapper.softDeleteBatch(tenantId, mappingIdsToDelete, now);

        if (n <= 0) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // API mapping 删除影响 Gateway 本地快照构建 → 广播受影响 serviceCodes（perm 未变，不 markRoles）
        PermissionChangeContext.markServiceCodes(tenantId, serviceCodes);

        OperationLogRuntimeContext.setSummary(
            "soft-deleted " + n + " resource_api_mapping row(s), ids=" + mappingIdsToDelete
        );
    }

    @Override
    public List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode) {
        return apiMappingMapper.selectValidList(tenantId, resourceId, serviceCode)
            .stream().map(this::toApiMappingResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "perm", action = "resource-api-mapping-update", targetType = "resource_api_mapping", targetId = "#req.mappingId()", summary = "'update api mapping ' + #req.mappingId()")
    public ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        ResourceApiMapping mapping = apiMappingMapper.selectValidById(tenantId, req.mappingId());
        if (mapping == null || !Objects.equals(mapping.getResourceEntityId(), req.resourceId())) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "API映射不存在: " + req.mappingId());
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

        // API mapping 变更影响 Gateway 本地快照 → 广播 serviceCode（perm 未变，不 markRoles）
        PermissionChangeContext.markServiceCodes(tenantId, mapping.getServiceCode());

        ResourceApiMapping updated = apiMappingMapper.selectValidById(req.mappingId(), tenantId);
        return toApiMappingResp(updated);
    }

    private Long resolveParentId(Long tenantId, ResourceCreateReq req) {
        if (!hasParentBusinessKey(req)) {
            return normalizeParentId(req.parentId());
        }
        ResourceResolveRequest parentReq = toParentResolveRequest(req);
        Long parentId = typeResolutionService.resolveResourceId(
            tenantId,
            parentReq.resourceTypeCode(),
            parentReq.resourceCode(),
            parentReq.codeType(),
            parentReq.domainCode()
        );
        if (parentId == null) {
            throw new BizException(
                PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "parent resource not found: " + parentKeyText(parentReq)
            );
        }
        return parentId;
    }

    private boolean hasParentBusinessKey(ResourceCreateReq req) {
        return req != null
            && req.parentResourceTypeCode() != null && !req.parentResourceTypeCode().isBlank()
            && req.parentResourceCode() != null && !req.parentResourceCode().isBlank();
    }

    private ResourceResolveRequest toParentResolveRequest(ResourceCreateReq req) {
        return new ResourceResolveRequest(
            req.parentResourceTypeCode(),
            req.parentResourceCode(),
            req.parentCodeType(),
            req.parentDomainCode()
        );
    }

    private Long normalizeParentId(Long parentId) {
        return parentId != null && parentId > 0 ? parentId : null;
    }

    private String parentKeyText(ResourceResolveRequest req) {
        return req.resourceTypeCode() + ":" + req.resourceCode();
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
