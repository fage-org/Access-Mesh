package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.DependencyManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 资源依赖管理服务实现类
 * <p>
 * 提供资源依赖关系的CRUD操作和批量同步功能。
 * 资源依赖定义了权限级联规则：当用户对源资源执行某操作时，
 * 如果该操作依赖目标资源的权限，系统会自动检查或授予目标资源权限。
 * 核心功能包括：
 * - 依赖关系创建、更新、删除
 * - 批量同步（全量/增量模式）
 * - 循环依赖检测
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量删除和批量同步采用批量SQL优化，避免N+1查询问题。
 * </p>
 */
@Service
public class DependencyManageServiceImpl implements DependencyManageService {

    private final ResourceDependencyMapper dependencyMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param dependencyMapper            资源依赖数据访问层
     * @param resourceEntityMapper        资源实体数据访问层
     * @param operationPermissionMapper   操作权限数据访问层
     * @param typeResolutionService       类型解析服务
     * @param operationLogDomainService   操作日志领域服务
     * @param engine                      权限查询引擎
     */
    public DependencyManageServiceImpl(ResourceDependencyMapper dependencyMapper,
                                        ResourceEntityMapper resourceEntityMapper,
                                        OperationPermissionMapper operationPermissionMapper,
                                        TypeResolutionService typeResolutionService,
                                        OperationLogDomainService operationLogDomainService,
                                        PermQueryEngine engine) {
        this.dependencyMapper = dependencyMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.operationLogDomainService = operationLogDomainService;
        this.engine = engine;
    }

    /**
     * 创建资源依赖关系
     * <p>
     * 创建源资源与目标资源之间的依赖关系。
     * 定义当用户对源资源执行特定操作时，需要目标资源的相应权限。
     * 需要DEPENDENCY_CREATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含源资源、目标资源、操作码等信息
     * @param operatorId 操作者ID，可选
     * @return 创建的资源依赖响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 源资源或目标资源不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on DEPENDENCY");
        }

        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new IllegalArgumentException("Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new IllegalArgumentException("Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
        }
        Long sourceOperationBits = resolveOperationBits(tenantId, req.sourceOperationCodes(), req.sourceResourceTypeCode());
        Long requiredOperationBits = resolveOperationBits(tenantId, req.requiredOperationCodes(), req.targetResourceTypeCode());

        ResourceDependency dep = new ResourceDependency();
        dep.setTenantId(tenantId);
        dep.setResourceEntityId(sourceId);
        dep.setDependsOnResourceEntityId(targetId);
        dep.setSourceOperationBits(sourceOperationBits);
        dep.setRequiredOperationBits(requiredOperationBits);
        dep.setAutoGrant(req.autoGrant() != null ? req.autoGrant() : true);
        dep.setDescription(req.description());
        dep.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        dep.setCreatedAt(now);
        dep.setUpdatedAt(now);
        dep.setDeleteFlag(0L);
        dependencyMapper.insert(dep);

        // 批量加载 ResourceEntity
        Set<Long> resourceIds = Set.of(sourceId, targetId);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);
        return toDependencyResp(dep, entityMap);
    }

    /**
     * 查询资源依赖关系列表
     * <p>
     * 根据资源实体ID过滤查询依赖关系列表。
     * 如果不指定资源实体ID，返回租户下所有依赖关系。
     * 批量加载ResourceEntity避免N+1查询问题。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID，可选过滤条件
     * @return 资源依赖响应列表
     */
    @Override
    public List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId) {
        List<ResourceDependency> dependencies = dependencyMapper.selectByTenantAndResourceEntityId(tenantId, resourceEntityId);

        // 批量加载 ResourceEntity 避免 N+1 查询
        Set<Long> resourceIds = extractResourceIds(dependencies);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);

        return dependencies.stream()
            .map(d -> toDependencyResp(d, entityMap))
            .collect(Collectors.toList());
    }

    /**
     * 查询所有资源依赖关系
     * <p>
     * 查询租户下所有活跃的资源依赖关系。
     * 批量加载ResourceEntity避免N+1查询问题。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 资源依赖响应列表
     */
    @Override
    public List<ResourceDependencyResp> listAllDependencies(Long tenantId) {
        List<ResourceDependency> dependencies = dependencyMapper.selectByTenantId(tenantId);

        // 批量加载 ResourceEntity 避免 N+1 查询
        Set<Long> resourceIds = extractResourceIds(dependencies);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);

        return dependencies.stream()
            .map(d -> toDependencyResp(d, entityMap))
            .collect(Collectors.toList());
    }

    /**
     * 更新资源依赖关系
     * <p>
     * 更新资源依赖关系的操作位、自动授权标志、描述等属性。
     * 需要DEPENDENCY_UPDATE权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含依赖ID和要更新的属性
     * @param operatorId 操作者ID，可选
     * @return 更新后的资源依赖响应
     * @throws SecurityException     无权限时抛出
     * @throws IllegalArgumentException 依赖关系不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, req.id(), OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on DEPENDENCY:" + req.id());
        }

        ResourceDependency dep = dependencyMapper.selectOneById(req.id());
        if (dep == null || dep.getDeleteFlag() != 0L || !tenantId.equals(dep.getTenantId())) {
            throw new IllegalArgumentException("Dependency not found: " + req.id());
        }
        if (req.sourceOperationCodes() != null) {
            dep.setSourceOperationBits(resolveOperationBits(tenantId, req.sourceOperationCodes(), req.sourceResourceTypeCode()));
        }
        if (req.requiredOperationCodes() != null) {
            dep.setRequiredOperationBits(resolveOperationBits(tenantId, req.requiredOperationCodes(), req.targetResourceTypeCode()));
        }
        if (req.autoGrant() != null) dep.setAutoGrant(req.autoGrant());
        if (req.description() != null) dep.setDescription(req.description());
        dep.setUpdatedAt(LocalDateTime.now());
        dependencyMapper.update(dep);

        // 批量加载 ResourceEntity
        Set<Long> resourceIds = Stream.of(dep.getResourceEntityId(), dep.getDependsOnResourceEntityId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);
        return toDependencyResp(dep, entityMap);
    }

    /**
     * 检测循环依赖
     * <p>
     * 检查添加新的依赖关系是否会形成循环依赖。
     * 通过构建依赖图并使用深度优先搜索检测是否存在从目标资源到源资源的路径。
     * 如果源资源和目标资源相同，直接返回存在循环。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      循环依赖检测请求，包含源资源和目标资源信息
     * @return 是否存在循环依赖，true表示添加该依赖会形成循环
     * @throws IllegalArgumentException 源资源或目标资源不存在时抛出
     */
    @Override
    public boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req) {
        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new IllegalArgumentException("Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new IllegalArgumentException("Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
        }
        if (Objects.equals(sourceId, targetId)) {
            return true;
        }
        List<ResourceDependency> allDeps = dependencyMapper.selectByTenantId(tenantId);
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (ResourceDependency dep : allDeps) {
            graph.computeIfAbsent(dep.getResourceEntityId(), k -> new HashSet<>())
                .add(dep.getDependsOnResourceEntityId());
        }
        graph.computeIfAbsent(sourceId, k -> new HashSet<>()).add(targetId);
        return canReach(graph, targetId, sourceId, new HashSet<>());
    }

    /**
     * 深度优先搜索检测路径可达性
     * <p>
     * 从当前节点出发，检测是否能到达目标节点。
     * 用于循环依赖检测。
     * </p>
     *
     * @param graph   依赖图，key为资源ID，value为该资源依赖的资源ID集合
     * @param current 当前访问的资源ID
     * @param target  目标资源ID
     * @param visited 已访问的资源ID集合，防止重复访问
     * @return 是否能从当前节点到达目标节点
     */
    private boolean canReach(Map<Long, Set<Long>> graph, Long current, Long target, Set<Long> visited) {
        if (Objects.equals(current, target)) return true;
        if (!visited.add(current)) return false;
        for (Long next : graph.getOrDefault(current, Set.of())) {
            if (canReach(graph, next, target, visited)) return true;
        }
        return false;
    }

    /**
     * 批量删除资源依赖关系
     * <p>
     * 批量软删除资源依赖关系。
     * 使用批量查询和批量软删除避免N+1问题。
     * 需要DEPENDENCY_DELETE权限。
     * </p>
     *
     * @param tenantId       租户ID
     * @param dependencyIds  资源依赖ID列表
     * @param operatorId     操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (dependencyIds == null || dependencyIds.isEmpty()) return;

        Set<Long> validInputIds = dependencyIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) return;

        engine.validateBatch(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, validInputIds, OperationCodeConstants.DELETE);

        List<ResourceDependency> entities = dependencyMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) return;

        Set<Long> validIds = entities.stream().map(ResourceDependency::getId).collect(Collectors.toSet());
        // 批量软删除（性能优化：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        dependencyMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);

        operationLogDomainService.asyncRecord(
            "perm", "resource-dependency-remove", "BATCH", tenantId,
            "soft-deleted " + validIds.size() + " resource_dependency row(s), ids=" + validIds,
            operatorId, null, null, tenantId
        );
    }

    /**
     * 批量同步资源依赖关系
     * <p>
     * 根据服务编码和维持来源批量同步资源依赖关系。
     * 支持全量同步（FULL）和增量同步模式。
     * 全量同步会删除不在同步列表中的依赖关系。
     * 批量解析资源ID避免N+1查询，批量插入新依赖关系优化性能。
     * 需要DEPENDENCY_SYNC权限。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        批量同步请求，包含同步模式、服务编码、维持来源和依赖项列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.SYNC)) {
            throw new SecurityException("Permission denied: SYNC on DEPENDENCY");
        }

        boolean isFullSync = "FULL".equalsIgnoreCase(req.syncMode());
        List<DependencyBatchSyncReq.DependencySyncItem> items = req.items() == null ? List.of() : req.items();
        LocalDateTime now = LocalDateTime.now();

        if (isFullSync) {
            List<ResourceDependency> existingDeps = dependencyMapper.selectByOwnerService(tenantId, req.serviceCode(), req.maintainSource());

            Set<Long> allResourceIds = existingDeps.stream()
                .flatMap(dep -> Stream.of(dep.getResourceEntityId(), dep.getDependsOnResourceEntityId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, ResourceEntity> resourceMap = allResourceIds.isEmpty() ? Map.of()
                : resourceEntityMapper.selectValidByIds(tenantId, allResourceIds).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

            // 收集需要删除的ID（性能优化：使用批量SQL代替循环更新）
            List<Long> idsToDelete = new java.util.ArrayList<>();
            for (ResourceDependency existing : existingDeps) {
                ResourceEntity sourceResource = resourceMap.get(existing.getResourceEntityId());
                ResourceEntity targetResource = resourceMap.get(existing.getDependsOnResourceEntityId());
                if (sourceResource == null || targetResource == null) continue;

                boolean stillPresent = items.stream().anyMatch(item ->
                    Objects.equals(item.sourceResourceCode(), sourceResource.getCode())
                        && Objects.equals(item.targetResourceCode(), targetResource.getCode()));
                if (!stillPresent) {
                    idsToDelete.add(existing.getId());
                }
            }
            // 批量软删除收集的ID
            if (!idsToDelete.isEmpty()) {
                dependencyMapper.softDeleteBatch(tenantId, idsToDelete, now);
            }
        }

        // ===== 批量解析以避免N+1查询 =====
        // 1. 从依赖项中收集所有唯一的资源请求
        List<ResourceResolveRequest> resourceRequests = items.stream()
            .flatMap(item -> Stream.of(
                new ResourceResolveRequest(item.sourceResourceTypeCode(), item.sourceResourceCode(), item.sourceCodeType(), null),
                new ResourceResolveRequest(item.targetResourceTypeCode(), item.targetResourceCode(), item.targetCodeType(), null)
            ))
            .filter(r -> r.resourceCode() != null && !r.resourceCode().isBlank())
            .distinct()
            .collect(Collectors.toList());

        // 2. 批量解析所有资源ID
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        // 3. 使用预解析的ID处理每个依赖项
        // 4. 批量查询现有依赖关系以避免循环中的N+1查询
        Set<Long> sourceResourceIds = new HashSet<>();
        Set<Long> targetResourceIds = new HashSet<>();
        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            ResourceResolveKey sourceKey = new ResourceResolveKey(
                item.sourceResourceTypeCode(), item.sourceResourceCode(), item.sourceCodeType(), null);
            ResourceResolveKey targetKey = new ResourceResolveKey(
                item.targetResourceTypeCode(), item.targetResourceCode(), item.targetCodeType(), null);
            Long sourceResourceId = resourceIdMap.get(sourceKey);
            Long targetResourceId = resourceIdMap.get(targetKey);
            if (sourceResourceId != null) sourceResourceIds.add(sourceResourceId);
            if (targetResourceId != null) targetResourceIds.add(targetResourceId);
        }

        // 构建现有依赖关系的复合键映射
        Map<String, ResourceDependency> existingDepMap = new HashMap<>();
        if (!sourceResourceIds.isEmpty() || !targetResourceIds.isEmpty()) {
            List<ResourceDependency> existingDeps = dependencyMapper.selectBySourceAndTargetIds(tenantId, sourceResourceIds, targetResourceIds);
            for (ResourceDependency dep : existingDeps) {
                String key = dep.getResourceEntityId() + ":" + dep.getDependsOnResourceEntityId();
                existingDepMap.put(key, dep);
            }
        }

        // 性能优化：收集插入项用于批量操作
        List<ResourceDependency> toInsert = new ArrayList<>();
        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            ResourceResolveKey sourceKey = new ResourceResolveKey(
                item.sourceResourceTypeCode(), item.sourceResourceCode(), item.sourceCodeType(), null);
            ResourceResolveKey targetKey = new ResourceResolveKey(
                item.targetResourceTypeCode(), item.targetResourceCode(), item.targetCodeType(), null);

            Long sourceResourceId = resourceIdMap.get(sourceKey);
            Long targetResourceId = resourceIdMap.get(targetKey);
            if (sourceResourceId == null || targetResourceId == null) continue;

            Long sourceOperationBits = resolveOperationBits(tenantId, item.sourceOperationCodes(), item.sourceResourceTypeCode());
            Long requiredOperationBits = resolveOperationBits(tenantId, item.requiredOperationCodes(), item.targetResourceTypeCode());

            String depKey = sourceResourceId + ":" + targetResourceId;
            ResourceDependency existing = existingDepMap.get(depKey);

            if (existing != null) {
                // 注意：更新仍然逐项执行，因为每个实体的字段值不同
                if (sourceOperationBits != null) existing.setSourceOperationBits(sourceOperationBits);
                if (requiredOperationBits != null) existing.setRequiredOperationBits(requiredOperationBits);
                if (item.autoGrant() != null) existing.setAutoGrant(item.autoGrant());
                if (item.description() != null) existing.setDescription(item.description());
                existing.setOwnerServiceCode(req.serviceCode());
                existing.setMaintainSource(req.maintainSource());
                existing.setUpdatedAt(now);
                dependencyMapper.update(existing);
            } else {
                ResourceDependency dep = new ResourceDependency();
                dep.setTenantId(tenantId);
                dep.setResourceEntityId(sourceResourceId);
                dep.setDependsOnResourceEntityId(targetResourceId);
                dep.setSourceOperationBits(sourceOperationBits);
                dep.setRequiredOperationBits(requiredOperationBits);
                dep.setAutoGrant(item.autoGrant() != null ? item.autoGrant() : true);
                dep.setDescription(item.description());
                dep.setOwnerServiceCode(req.serviceCode());
                dep.setMaintainSource(req.maintainSource());
                dep.setCreatedBy(operatorId);
                dep.setCreatedAt(now);
                dep.setUpdatedAt(now);
                dep.setDeleteFlag(0L);
                toInsert.add(dep);
            }
        }
        // 性能优化：批量插入代替循环插入
        if (!toInsert.isEmpty()) {
            dependencyMapper.insertBatch(toInsert);
        }
    }

    /**
     * 解析操作码为操作位掩码
     * <p>
     * 将操作码列表转换为对应的二进制位掩码。
     * 批量解析操作ID，然后合并所有操作权限的binaryBit。
     * </p>
     *
     * @param tenantId         租户ID
     * @param operationCodes   操作码列表
     * @param resourceTypeCode 资源类型编码
     * @return 操作位掩码，如果操作码列表为空返回null
     */
    private Long resolveOperationBits(Long tenantId, List<String> operationCodes, String resourceTypeCode) {
        if (operationCodes == null || operationCodes.isEmpty()) return null;

        Set<String> codeSet = new HashSet<>(operationCodes);
        Map<String, Long> codeToIdMap = typeResolutionService.batchResolveOperationIds(tenantId, resourceTypeCode, codeSet);
        Set<Long> opIds = codeToIdMap.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (opIds.isEmpty()) return 0L;

        Map<Long, OperationPermission> opMap = batchLoadOperations(tenantId, opIds);
        Long bits = 0L;
        for (OperationPermission op : opMap.values()) {
            if (op.getBinaryBit() != null) bits |= op.getBinaryBit();
        }
        return bits;
    }

    /**
     * 将ResourceDependency实体转换为响应对象
     * <p>
     * 转换时从entityMap中获取源资源和目标资源的编码。
     * </p>
     *
     * @param d        资源依赖实体
     * @param entityMap 资源实体映射表
     * @return 资源依赖响应对象
     */
    private ResourceDependencyResp toDependencyResp(ResourceDependency d, Map<Long, ResourceEntity> entityMap) {
        ResourceEntity src = entityMap.get(d.getResourceEntityId());
        ResourceEntity dep = entityMap.get(d.getDependsOnResourceEntityId());
        return new ResourceDependencyResp(
            d.getId(), d.getTenantId(), d.getResourceEntityId(),
            src != null ? src.getCode() : null,
            d.getDependsOnResourceEntityId(),
            dep != null ? dep.getCode() : null,
            d.getSourceOperationBits(), d.getRequiredOperationBits(),
            d.getAutoGrant(), d.getDescription(), d.getCreatedAt()
        );
    }

    /**
     * 批量加载ResourceEntity并构建映射表
     * <p>
     * 批量查询资源实体，避免N+1查询问题。
     * </p>
     *
     * @param tenantId   租户ID
     * @param resourceIds 资源ID集合
     * @return 资源实体映射表，key为资源ID，value为资源实体
     */
    private Map<Long, ResourceEntity> loadResourceEntityMap(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }
        return resourceEntityMapper.selectValidByIds(tenantId, resourceIds).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));
    }

    /**
     * 从依赖列表中提取所有相关的ResourceEntity ID
     * <p>
     * 收集所有源资源ID和目标资源ID，用于批量加载。
     * </p>
     *
     * @param dependencies 资源依赖列表
     * @return 资源ID集合
     */
    private Set<Long> extractResourceIds(List<ResourceDependency> dependencies) {
        return dependencies.stream()
            .flatMap(d -> Stream.of(d.getResourceEntityId(), d.getDependsOnResourceEntityId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    // ===== 私有批量加载方法 =====

    /**
     * 批量加载操作权限
     */
    private Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return operationPermissionMapper.selectValidByIds(tenantId, ids)
            .stream().collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));
    }
}