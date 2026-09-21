package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.resource.dto.req.AutoGrantExplainReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.DependencyDeclarationStatusReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.access.resource.dto.resp.AutoGrantExplainResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.DependencyDeclarationStatusResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantInsightDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceManifestSyncMapper;
import cn.ac.fage.accessmesh.access.resource.service.DependencyAppService;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.PermissionManifestNormalizer;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 编译依赖图的只读查询；全部入口使用类型级 DEPENDENCY:VIEW 门禁。 */
@Service
public class DependencyAppServiceImpl implements DependencyAppService {
    private final ResourceDependencyMapper dependencyMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;
    private final AutoGrantInsightDomainService autoGrantInsightDomainService;
    private final OperationPermissionDomainService operationPermissionDomainService;
    private final PermissionConditionDomainService conditionDomainService;
    private final PermissionManifestNormalizer manifestNormalizer;
    private final DependencyCompilationDomainService compilation;
    private final ServiceManifestSyncMapper manifestSyncMapper;

    public DependencyAppServiceImpl(ResourceDependencyMapper dependencyMapper,
                                    ResourceEntityMapper resourceEntityMapper,
                                    TypeResolutionService typeResolutionService,
                                    PermQueryEngine engine,
                                    AutoGrantInsightDomainService autoGrantInsightDomainService,
                                    OperationPermissionDomainService operationPermissionDomainService,
                                    PermissionConditionDomainService conditionDomainService,
                                    PermissionManifestNormalizer manifestNormalizer,
                                    DependencyCompilationDomainService compilation,
                                    ServiceManifestSyncMapper manifestSyncMapper) {
        this.dependencyMapper = dependencyMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.autoGrantInsightDomainService = autoGrantInsightDomainService;
        this.operationPermissionDomainService = operationPermissionDomainService;
        this.conditionDomainService = conditionDomainService;
        this.manifestNormalizer = manifestNormalizer;
        this.compilation = compilation;
        this.manifestSyncMapper = manifestSyncMapper;
    }

    /**
     * 查询资源依赖关系列表
     * <p>
     * 类型级 DEPENDENCY:VIEW 门禁。
     * 根据资源实体ID过滤查询依赖关系列表（设计定案：全量不分页，量小非流水表，
     * 对齐 condition/conflict-rule；resourceEntityId 为内部主键过滤参数，前端本地过滤）。
     * 批量加载ResourceEntity避免N+1查询问题。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID，可选过滤条件
     * @return 资源依赖响应列表
     * @throws SecurityException 无 VIEW 权限时抛出
     */
    @Override
    public List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        List<ResourceDependency> dependencies = dependencyMapper.selectByTenantAndResourceEntityId(tenantId, resourceEntityId);
        return toDependencyResps(tenantId, dependencies);
    }

    /**
     * 查询所有资源依赖关系
     * <p>
     * 类型级 DEPENDENCY:VIEW 门禁（graph 端点全量分支）。
     * 查询租户下所有活跃的资源依赖关系。
     * 批量加载ResourceEntity避免N+1查询问题。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 资源依赖响应列表
     * @throws SecurityException 无 VIEW 权限时抛出
     */
    @Override
    public List<ResourceDependencyResp> listAllDependencies(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        List<ResourceDependency> dependencies = dependencyMapper.selectByTenantId(tenantId);
        return toDependencyResps(tenantId, dependencies);
    }

    /**
     * 检测循环依赖
     * <p>
     * 类型级 DEPENDENCY:VIEW 门禁（纯查询透出全租户依赖图数据）。
     * 检查添加新的依赖关系是否会形成循环依赖（业务键定位）。
     * 通过构建依赖图并使用深度优先搜索检测是否存在从目标资源到源资源的路径。
     * 如果源资源和目标资源相同，直接返回存在循环。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      循环依赖检测请求，包含源资源和目标资源信息
     * @return 是否存在循环依赖，true表示添加该依赖会形成循环
     * @throws SecurityException 无 VIEW 权限时抛出
     * @throws BizException      源资源或目标资源不存在时抛出
     */
    @Override
    public boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new BizException(AccessErrorCode.RESOURCE_NOT_FOUND.getCode(), "Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new BizException(AccessErrorCode.RESOURCE_NOT_FOUND.getCode(), "Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
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
     * 角色自动授权来源解释（T-PERM-073，契约 §12.3.1）。
     * <p>类型级 DEPENDENCY:VIEW 门禁 + 角色业务键解析（失败 20001 明确抛出，不以空结果
     * 掩盖）。target 非空时逐项解析业务键：类型 20007 / 资源 20004 / 操作 20005（任何类型
     * 都不存在）与 20008（存在于其他类型）/ 条件 20006——与写入口业务键解析同口径。
     * REPEATABLE_READ 只读事务提供单次一致视图（设计 §11），不取写锁、不写数据。</p>
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AutoGrantExplainResp explainAutoGrant(Long tenantId, AutoGrantExplainReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        Long roleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (roleId == null) {
            throw new BizException(AccessErrorCode.ROLE_NOT_FOUND.getCode(),
                "role not found: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }
        AutoGrantInsightDomainService.ExplainTargetFact target = null;
        if (req.target() != null) {
            target = resolveExplainTarget(tenantId, req.target());
        }
        return autoGrantInsightDomainService.explain(tenantId, roleId, target,
            new AutoGrantInsightDomainService.ExplainLimits(
                req.resolvedMaxDepth(), req.resolvedMaxNodes(), req.resolvedMaxEdges()));
    }

    /** explain 目标事实解析：资源业务键 + 操作码（20005/20008 区分）+ 条件身份（20006）。 */
    private AutoGrantInsightDomainService.ExplainTargetFact resolveExplainTarget(
            Long tenantId, AutoGrantExplainReq.Target target) {
        Integer typeValue = typeResolutionService.resolveTypeValue(
            tenantId, "resource_type", target.resourceTypeCode());
        if (typeValue == null) {
            throw new BizException(AccessErrorCode.RESOURCE_TYPE_NOT_FOUND.getCode(),
                "resourceTypeCode not found: " + target.resourceTypeCode());
        }
        String codeType = target.codeType() == null || target.codeType().isBlank()
            ? "default" : target.codeType();
        Long resourceId = typeResolutionService.resolveResourceId(
            tenantId, target.resourceTypeCode(), target.resourceCode(), codeType, null);
        if (resourceId == null) {
            throw new BizException(AccessErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "target resource not found: " + target.resourceTypeCode() + "/" + target.resourceCode());
        }
        List<OperationPermission> allOperations = operationPermissionDomainService
            .selectAllOperationsByTenant(tenantId);
        OperationPermission operation = allOperations.stream()
            .filter(op -> Objects.equals(op.getResourceType(), typeValue))
            .filter(op -> target.operationCode().equals(op.getCode()))
            .findFirst().orElse(null);
        if (operation == null) {
            boolean knownCode = allOperations.stream()
                .anyMatch(op -> target.operationCode().equals(op.getCode()));
            throw new BizException(knownCode
                ? AccessErrorCode.RESOURCE_TYPE_OPERATION_MISMATCH.getCode()
                : AccessErrorCode.OPERATION_NOT_FOUND.getCode(),
                "target operationCode does not resolve: " + target.operationCode());
        }
        Long conditionId = target.conditionId();
        if (conditionId != null && conditionDomainService
                .selectValidConditionsByIds(tenantId, Set.of(conditionId)).isEmpty()) {
            throw new BizException(AccessErrorCode.CONDITION_NOT_FOUND.getCode(),
                "target conditionId not found: " + conditionId);
        }
        return new AutoGrantInsightDomainService.ExplainTargetFact(
            resourceId, operation.getBinaryBit(), conditionId);
    }

    /**
     * 依赖声明诊断（T-PERM-073，契约 §12.3 declaration-status）。
     * <p>类型级 DEPENDENCY:VIEW 门禁。发布状态行 + 声明行（payload 业务键回显）；
     * payload 损坏防御性降级为 null 业务字段（诊断行仍可见状态与拒绝原因）。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public DependencyDeclarationStatusResp declarationStatus(Long tenantId, DependencyDeclarationStatusReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        String serviceFilter = req == null || req.sourceService() == null || req.sourceService().isBlank()
            ? null : req.sourceService();
        List<DependencyDeclarationStatusResp.ManifestSyncItem> manifestSyncs =
            manifestSyncMapper.selectByTenantId(tenantId).stream()
                .filter(state -> serviceFilter == null || serviceFilter.equals(state.getSourceService()))
                .map(state -> new DependencyDeclarationStatusResp.ManifestSyncItem(
                    state.getSourceService(), state.getPublicationGeneration(), state.getRevision(),
                    state.getSyncStatus(), state.getIsDirty(), state.getLastSyncedAt()))
                .toList();
        List<DependencyDeclarationStatusResp.DeclarationItem> declarations =
            compilation.loadDeclarations(tenantId).stream()
                .filter(declaration -> serviceFilter == null
                    || serviceFilter.equals(declaration.getSourceService()))
                .map(this::toDeclarationItem)
                .toList();
        return new DependencyDeclarationStatusResp(manifestSyncs, declarations);
    }

    /** 声明行 → 诊断响应项（payload 解析失败时业务键字段降级 null，状态面保持可见）。 */
    private DependencyDeclarationStatusResp.DeclarationItem toDeclarationItem(
            PermissionDependencyDeclaration declaration) {
        String sourceType = null, sourceCode = null, sourceCodeType = null, sourceOperation = null;
        String targetType = null, targetCode = null, targetCodeType = null, description = null;
        List<String> requiredOperationCodes = List.of();
        try {
            var parsed = manifestNormalizer.readDeclaration(declaration.getDeclarationPayload());
            sourceType = parsed.source().resourceTypeCode();
            sourceCode = parsed.source().resourceCode();
            sourceCodeType = parsed.source().codeType();
            sourceOperation = parsed.sourceOperationCode();
            targetType = parsed.target().resourceTypeCode();
            targetCode = parsed.target().resourceCode();
            targetCodeType = parsed.target().codeType();
            requiredOperationCodes = parsed.requiredOperationCodes();
            description = parsed.description();
        } catch (Exception ignored) {
            // payload 损坏（DB 直写/历史脏数据）：诊断行降级渲染，不阻断整面
        }
        return new DependencyDeclarationStatusResp.DeclarationItem(
            declaration.getId(), declaration.getSourceService(), declaration.getDeclarationKey(),
            declaration.getCompileStatus(), declaration.getRejectReason(), description,
            sourceType, sourceCode, sourceCodeType, sourceOperation,
            targetType, targetCode, targetCodeType, requiredOperationCodes,
            declaration.getUpdatedAt());
    }

    /**
     * 列表响应组装（批量加载 ResourceEntity 与类型编码反查，避免 N+1）
     */
    private List<ResourceDependencyResp> toDependencyResps(Long tenantId, List<ResourceDependency> dependencies) {
        Set<Long> resourceIds = extractResourceIds(dependencies);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);
        Map<Integer, String> typeCodeIndex = loadTypeCodeIndex(tenantId, entityMap);
        return dependencies.stream()
            .map(d -> toDependencyResp(d, entityMap, typeCodeIndex))
            .collect(Collectors.toList());
    }

    /**
     * 将ResourceDependency实体转换为响应对象
     * <p>
     * 转换时从entityMap中获取源/目标资源的编码、名称与类型编码（资源已软删时为 null，
     * 前端回退展示内部实体ID）。
     * </p>
     *
     * @param d              资源依赖实体
     * @param entityMap      资源实体映射表
     * @param typeCodeByValue 资源类型值 -> 类型编码映射（type_definition 反查）
     * @return 资源依赖响应对象
     */
    private ResourceDependencyResp toDependencyResp(ResourceDependency d, Map<Long, ResourceEntity> entityMap,
                                                    Map<Integer, String> typeCodeByValue) {
        ResourceEntity src = entityMap.get(d.getResourceEntityId());
        ResourceEntity dep = entityMap.get(d.getDependsOnResourceEntityId());
        return new ResourceDependencyResp(
            d.getId(), d.getTenantId(), d.getResourceEntityId(),
            src != null ? src.getCode() : null,
            src != null ? typeCodeByValue.get(src.getResourceType()) : null,
            src != null ? src.getName() : null,
            d.getDependsOnResourceEntityId(),
            dep != null ? dep.getCode() : null,
            dep != null ? typeCodeByValue.get(dep.getResourceType()) : null,
            dep != null ? dep.getName() : null,
            d.getSourceOperationBits(), d.getRequiredOperationBits(),
            d.getDescription(),
            d.getOwnerServiceCode(), d.getMaintainSource(),
            d.getCreatedAt(), d.getUpdatedAt()
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
     * 资源类型值 -> 类型编码批量反查（Resp 补 source/targetResourceTypeCode）
     */
    private Map<Integer, String> loadTypeCodeIndex(Long tenantId, Map<Long, ResourceEntity> entityMap) {
        Set<Integer> typeValues = entityMap.values().stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (typeValues.isEmpty()) {
            return Map.of();
        }
        return typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", typeValues);
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

}
