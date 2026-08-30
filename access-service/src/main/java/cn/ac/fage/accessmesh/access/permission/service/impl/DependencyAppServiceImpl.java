package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.service.DependencyAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.OperatorUtil;
import com.mybatisflex.core.util.UpdateEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

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
 * 权限门禁（T-PERM-031 口径）：读 list/graph/check = DEPENDENCY:VIEW、
 * 写 create/update/remove = DEPENDENCY:CREATE/UPDATE/DELETE、batch-sync = DEPENDENCY:SYNC，
 * 全部类型级（DEPENDENCY 无 resource_entity 实例投影，实例级授权无从配置）。
 * remove 类型级全有或全无，幽灵 id 解析阶段静默跳过（幂等）。
 * 批量删除和批量同步采用批量SQL优化，避免N+1查询问题。
 * </p>
 */
@Service
public class DependencyAppServiceImpl implements DependencyAppService {

    /** 管理端创建行的维护来源（schema 四值之一；批量同步 FULL diff 按 owner+maintainSource 隔离清理范围） */
    private static final String MAINTAIN_SOURCE_ADMIN_UI = "ADMIN_UI";

    private final ResourceDependencyMapper dependencyMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param dependencyMapper            资源依赖数据访问层
     * @param resourceEntityMapper        资源实体数据访问层
     * @param operationPermissionMapper   操作权限数据访问层
     * @param typeResolutionService       类型解析服务
     * @param engine                      权限查询引擎
     */
    public DependencyAppServiceImpl(ResourceDependencyMapper dependencyMapper,
                                        ResourceEntityMapper resourceEntityMapper,
                                        OperationPermissionMapper operationPermissionMapper,
                                        TypeResolutionService typeResolutionService,
                                        PermQueryEngine engine) {
        this.dependencyMapper = dependencyMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    /**
     * 创建资源依赖关系
     * <p>
     * 创建源资源与目标资源之间的依赖关系（业务键定位）。
     * 类型级 DEPENDENCY:CREATE 门禁；未知资源 20004、未知操作码 20005（fail-closed，
     * 不再静默丢弃）、自依赖 20044、等价重复 20054（业务预查 + DB uk 兜底）。
     * 管理端创建行 maintainSource=ADMIN_UI、ownerServiceCode=null。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        创建请求，包含源资源、目标资源、操作码等信息
     * @param operatorId 操作者ID，可选
     * @return 创建的资源依赖响应
     * @throws SecurityException     无权限时抛出
     * @throws BizException          资源/操作不存在、自依赖、重复依赖或 autoGrant=true 时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_DEPENDENCY_CREATE", targetType = "resource_dependency", targetId = "#result.id()", summary = "'create resource dependency'")
    public ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied: CREATE on DEPENDENCY");
        }
        rejectAutoGrantTrue(req.autoGrant());

        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
        }
        rejectSelfDependency(sourceId, targetId);
        Long sourceOperationBits = resolveOperationBits(tenantId, req.sourceOperationCodes(), req.sourceResourceTypeCode());
        Long requiredOperationBits = resolveOperationBits(tenantId, req.requiredOperationCodes(), req.targetResourceTypeCode());

        assertNotDuplicate(tenantId, sourceId, targetId, sourceOperationBits, null);

        ResourceDependency dep = new ResourceDependency();
        dep.setTenantId(tenantId);
        dep.setResourceEntityId(sourceId);
        dep.setDependsOnResourceEntityId(targetId);
        dep.setSourceOperationBits(sourceOperationBits);
        dep.setRequiredOperationBits(requiredOperationBits);
        dep.setAutoGrant(Boolean.TRUE.equals(req.autoGrant()));
        dep.setDescription(req.description());
        dep.setOwnerServiceCode(null);
        dep.setMaintainSource(MAINTAIN_SOURCE_ADMIN_UI);
        dep.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        dep.setCreatedAt(now);
        dep.setUpdatedAt(now);
        dep.setDeleteFlag(0L);
        try {
            dependencyMapper.insert(dep);
        } catch (DataIntegrityViolationException e) {
            if (isDependencyUniqueViolation(e)) {
                throw new BizException(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode(),
                    PermissionErrorCode.DEPENDENCY_DUPLICATE.getMessage());
            }
            throw e;
        }

        // 批量加载 ResourceEntity
        Set<Long> resourceIds = Set.of(sourceId, targetId);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);
        return toDependencyResp(dep, entityMap, loadTypeCodeIndex(tenantId, entityMap));
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
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.VIEW)) {
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
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        List<ResourceDependency> dependencies = dependencyMapper.selectByTenantId(tenantId);
        return toDependencyResps(tenantId, dependencies);
    }

    /**
     * 更新资源依赖关系（PUT 全量覆盖语义）
     * <p>
     * 先解析后门禁（T-PERM-029 模式：未知 id 优先 20019 且零副作用），再做类型级
     * DEPENDENCY:UPDATE 门禁。资源对按业务键重新解析、可修改（UpdateEntity 显式写列，
     * sourceOperationCodes=null 清空为"任意操作触发"、description=null 清空）；
     * maintainSource/ownerServiceCode 不随管理端编辑改写（来源归属仅批量同步侧变更）。
     * 等价重复（同源+同目标+同 COALESCE(source_bits,0)）预查拒绝 20054，DB uk 兜底。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        更新请求，包含依赖ID和全量字段
     * @param operatorId 操作者ID，可选
     * @return 更新后的资源依赖响应
     * @throws SecurityException 无权限时抛出
     * @throws BizException      依赖不存在（20019）、资源/操作不存在、自依赖、重复（20054）或 autoGrant=true 时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_DEPENDENCY_UPDATE", targetType = "resource_dependency", targetId = "#req.id()", summary = "'update resource dependency ' + #req.id()")
    public ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId) {
        // 先解析后门禁（T-PERM-029 模式）
        ResourceDependency existing = dependencyMapper.selectOneById(req.id());
        if (existing == null || existing.getDeleteFlag() != 0L || !tenantId.equals(existing.getTenantId())) {
            throw new BizException(PermissionErrorCode.DEPENDENCY_NOT_FOUND.getCode(), "Dependency not found: " + req.id());
        }
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        // 类型级门禁（T-PERM-031 口径收窄，同 CONDITION/CONFLICT_RULE：DEPENDENCY 无
        // resource_entity 实例投影，原「编码轨传内部 id」的实例级声称系 ID 空间错位
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.UPDATE)) {
            throw new SecurityException("Permission denied: UPDATE on DEPENDENCY:" + req.id());
        }
        rejectAutoGrantTrue(req.autoGrant());

        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
        }
        rejectSelfDependency(sourceId, targetId);
        Long sourceOperationBits = resolveOperationBits(tenantId, req.sourceOperationCodes(), req.sourceResourceTypeCode());
        Long requiredOperationBits = resolveOperationBits(tenantId, req.requiredOperationCodes(), req.targetResourceTypeCode());

        assertNotDuplicate(tenantId, sourceId, targetId, sourceOperationBits, req.id());

        // PUT 全量覆盖（UpdateEntity 显式写列，对齐 conflict-rule 更新范式）：
        // null 语义可达——sourceOperationBits=null 即"任意操作触发"、description=null 即清空
        ResourceDependency patch = UpdateEntity.of(ResourceDependency.class);
        patch.setId(req.id());
        patch.setResourceEntityId(sourceId);
        patch.setDependsOnResourceEntityId(targetId);
        patch.setSourceOperationBits(sourceOperationBits);
        patch.setRequiredOperationBits(requiredOperationBits);
        patch.setAutoGrant(Boolean.TRUE.equals(req.autoGrant()));
        patch.setDescription(req.description());
        patch.setUpdatedAt(LocalDateTime.now());
        patch.setUpdatedBy(operatorId);
        try {
            dependencyMapper.update(patch);
        } catch (DataIntegrityViolationException e) {
            if (isDependencyUniqueViolation(e)) {
                throw new BizException(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode(),
                    PermissionErrorCode.DEPENDENCY_DUPLICATE.getMessage());
            }
            throw e;
        }

        // 极小并发窗口内（本事务外）行被并发软删时 re-select 可为 null——按 20019 收口而非 NPE 500
        ResourceDependency updated = dependencyMapper.selectOneById(req.id());
        if (updated == null || updated.getDeleteFlag() != 0L || !tenantId.equals(updated.getTenantId())) {
            throw new BizException(PermissionErrorCode.DEPENDENCY_NOT_FOUND.getCode(), "Dependency not found: " + req.id());
        }
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId,
            Stream.of(updated.getResourceEntityId(), updated.getDependsOnResourceEntityId())
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return toDependencyResp(updated, entityMap, loadTypeCodeIndex(tenantId, entityMap));
    }

    /**
     * autoGrant=true 写入拒绝（fail-closed）：自动授权未实现（T-PERM-035 暂缓），
     * 所有写入口（create/update/batch-sync）在落库前统一拦截，禁止静默接受。
     */
    private static void rejectAutoGrantTrue(Boolean autoGrant) {
        if (Boolean.TRUE.equals(autoGrant)) {
            throw new BizException(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(),
                PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getMessage());
        }
    }

    /**
     * 自依赖拒绝（source==target 即成环，check 端点同判定）：前端表单已校验，后端兜底。
     */
    private static void rejectSelfDependency(Long sourceId, Long targetId) {
        if (Objects.equals(sourceId, targetId)) {
            throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                "源资源与目标资源不能相同（自依赖成环）");
        }
    }

    /**
     * 等价依赖预查（T-PERM-031 设计定案，对齐 conflict-rule 20032 先例）：
     * 同源资源 + 同目标资源 + 同 COALESCE(source_operation_bits,0) 即等价
     * （uk_resource_dependency 唯一语义，source_bits NULL 与 0 同档），命中拒绝 20054；
     * 并发窗口由 DB 唯一索引兜底转同码。
     *
     * @param excludeId 更新场景排除自身行，创建传 null
     */
    private void assertNotDuplicate(Long tenantId, Long sourceId, Long targetId, Long sourceBits, Long excludeId) {
        long newBits = sourceBits == null ? 0L : sourceBits;
        for (ResourceDependency row : dependencyMapper.selectBySourceAndTargetIds(tenantId, Set.of(sourceId), Set.of(targetId))) {
            if (excludeId != null && excludeId.equals(row.getId())) continue;
            long rowBits = row.getSourceOperationBits() == null ? 0L : row.getSourceOperationBits();
            if (rowBits == newBits) {
                throw new BizException(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode(),
                    PermissionErrorCode.DEPENDENCY_DUPLICATE.getMessage());
            }
        }
    }

    /**
     * 判定 DataIntegrityViolationException 是否由依赖唯一约束违反引起
     * （PG 消息含约束名 uk_resource_dependency，用于预查失效时的并发兜底）。
     */
    private boolean isDependencyUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains("uk_resource_dependency")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DEPENDENCY");
        }
        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new BizException(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), "Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
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
     * 类型级 DEPENDENCY:DELETE 门禁（全有或全无）。
     * 幽灵 id（不存在/已软删）解析阶段静默跳过（幂等，响应无行数，调用方以事后查询核对）。
     * 使用批量查询和批量软删除避免N+1问题。
     * </p>
     *
     * @param tenantId       租户ID
     * @param dependencyIds  资源依赖ID列表
     * @param operatorId     操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_DEPENDENCY_REMOVE", targetType = "resource_dependency", targetId = "", summary = "'batch remove resource dependencies'")
    public void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (dependencyIds == null || dependencyIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = dependencyIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // 类型级门禁（T-PERM-031 口径收窄，同 CONDITION/CONFLICT_RULE 全有或全无）
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.DELETE)) {
            throw new SecurityException("Permission denied: DELETE on DEPENDENCY");
        }

        List<ResourceDependency> entities = dependencyMapper.selectValidByIds(tenantId, validInputIds);
        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream().map(ResourceDependency::getId).collect(Collectors.toSet());
        // 批量软删除（性能优化：使用单条SQL代替循环）
        LocalDateTime now = LocalDateTime.now();
        dependencyMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " resource_dependency row(s)");
    }

    /**
     * 批量同步资源依赖关系
     * <p>
     * 类型级 DEPENDENCY:SYNC 门禁。maintainSource 四值白名单由 DTO @Pattern 拒绝。
     * 资源 ID 与操作位在循环外按资源类型批量预解析（消解逐项 resolve 的 N+1）；
     * 资源未解析的条目静默跳过（同步清单漂移，与单条写入口 20004 fail-closed 不同，
     * 由对账兜底）；操作码未解析 fail-closed 20005。
     * FULL diff 匹配三元组（源+目标+COALESCE(source_bits,0)，对齐 uk 语义——同资源对
     * 不同触发操作是不同规则，仅按资源对匹配会误删）；upsert 同键三元组定位。
     * 只清理同一 ownerServiceCode=serviceCode + maintainSource 范围内的规则。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        批量同步请求，包含同步模式、服务编码、维持来源和依赖项列表
     * @param operatorId 操作者ID，可选
     * @throws SecurityException 无权限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_DEPENDENCY_SYNC", targetType = "resource_dependency", targetId = "#req.serviceCode()", summary = "'sync resource dependencies for service ' + #req.serviceCode()")
    public void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationCodeConstants.SYNC)) {
            throw new SecurityException("Permission denied: SYNC on DEPENDENCY");
        }

        boolean isFullSync = "FULL".equalsIgnoreCase(req.syncMode());
        List<DependencyBatchSyncReq.DependencySyncItem> items = req.items() == null ? List.of() : req.items();
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = 0;
        int updatedCount = 0;

        // 清单级预检（畸形清单零副作用，先于资源解析与 FULL diff 全部写操作）：
        // autoGrant=true 拒绝 + requiredOperationCodes 必填非空非全空白——原必检查位于
        // 「资源未解析条目跳过」之后，畸形条目会绕过 20044 且 FULL 仍照常执行差异删除
        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            rejectAutoGrantTrue(item.autoGrant());
            if (!hasCodes(item.requiredOperationCodes()) || nonBlankCodes(item.requiredOperationCodes()).isEmpty()) {
                throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
                    "sync item missing requiredOperationCodes: " + item.sourceResourceCode() + " -> " + item.targetResourceCode());
            }
        }

        // ===== 循环外批量预解析（资源 ID + 操作位索引，消解逐项解析的 N+1） =====
        List<ResourceResolveRequest> resourceRequests = items.stream()
            .flatMap(item -> Stream.of(
                new ResourceResolveRequest(item.sourceResourceTypeCode(), item.sourceResourceCode(), item.sourceCodeType(), null),
                new ResourceResolveRequest(item.targetResourceTypeCode(), item.targetResourceCode(), item.targetCodeType(), null)
            ))
            .filter(r -> r.resourceCode() != null && !r.resourceCode().isBlank())
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resourceIdMap = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);
        Map<String, Map<String, Long>> opBitIndex = buildOperationBitIndex(tenantId, items);

        // 预解析条目：资源未解析的条目静默跳过（同步清单漂移由对账兜底）；
        // 必填操作码已在清单级预检拒绝，此处 requiredBits 不可能为 null
        List<ResolvedSyncItem> resolvedItems = new ArrayList<>();
        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            Long sourceResourceId = resourceIdMap.get(new ResourceResolveKey(
                item.sourceResourceTypeCode(), item.sourceResourceCode(), item.sourceCodeType(), null));
            Long targetResourceId = resourceIdMap.get(new ResourceResolveKey(
                item.targetResourceTypeCode(), item.targetResourceCode(), item.targetCodeType(), null));
            if (sourceResourceId == null || targetResourceId == null) continue;
            Long requiredBits = bitsOf(item.requiredOperationCodes(), item.targetResourceTypeCode(), opBitIndex);
            resolvedItems.add(new ResolvedSyncItem(sourceResourceId, targetResourceId,
                bitsOf(item.sourceOperationCodes(), item.sourceResourceTypeCode(), opBitIndex),
                requiredBits, item));
        }

        if (isFullSync) {
            List<ResourceDependency> existingDeps = dependencyMapper.selectByOwnerService(tenantId, req.serviceCode(), req.maintainSource());

            // FULL diff 三元组匹配（源+目标+COALESCE(source_bits,0)，对齐 uk_resource_dependency）：
            // 同资源对不同触发操作是不同规则，仅按资源对匹配会误删（T-PERM-031 修复）
            List<Long> idsToDelete = new java.util.ArrayList<>();
            for (ResourceDependency existing : existingDeps) {
                long existingBits = existing.getSourceOperationBits() == null ? 0L : existing.getSourceOperationBits();
                boolean stillPresent = resolvedItems.stream().anyMatch(ri ->
                    Objects.equals(ri.sourceId(), existing.getResourceEntityId())
                        && Objects.equals(ri.targetId(), existing.getDependsOnResourceEntityId())
                        && (ri.sourceBits() == null ? 0L : ri.sourceBits()) == existingBits);
                if (!stillPresent) {
                    idsToDelete.add(existing.getId());
                }
            }
            // 批量软删除收集的ID
            if (!idsToDelete.isEmpty()) {
                dependencyMapper.softDeleteBatch(tenantId, idsToDelete, now);
                deletedCount = idsToDelete.size();
            }
        }

        // 现有依赖三元组键映射（upsert 匹配，对齐 uk 语义；空集合跳过查询）
        Set<Long> sourceResourceIds = new HashSet<>();
        Set<Long> targetResourceIds = new HashSet<>();
        for (ResolvedSyncItem ri : resolvedItems) {
            sourceResourceIds.add(ri.sourceId());
            targetResourceIds.add(ri.targetId());
        }
        Map<String, ResourceDependency> existingDepMap = new HashMap<>();
        if (!sourceResourceIds.isEmpty() || !targetResourceIds.isEmpty()) {
            List<ResourceDependency> existingDeps = dependencyMapper.selectBySourceAndTargetIds(tenantId, sourceResourceIds, targetResourceIds);
            for (ResourceDependency dep : existingDeps) {
                existingDepMap.put(depKey(dep.getResourceEntityId(), dep.getDependsOnResourceEntityId(), dep.getSourceOperationBits()), dep);
            }
        }

        // 性能优化：收集插入项用于批量操作
        List<ResourceDependency> toInsert = new ArrayList<>();
        for (ResolvedSyncItem ri : resolvedItems) {
            String key = depKey(ri.sourceId(), ri.targetId(), ri.sourceBits());
            ResourceDependency existing = existingDepMap.get(key);

            if (existing != null) {
                // 注意：更新仍然逐项执行，因为每个实体的字段值不同；
                // 空值不覆盖（同步清单部分字段缺省保留原值，FULL 的清除语义由 diff 删除分支承担）
                if (ri.sourceBits() != null) existing.setSourceOperationBits(ri.sourceBits());
                if (ri.item().description() != null) existing.setDescription(ri.item().description());
                existing.setRequiredOperationBits(ri.requiredBits());
                existing.setOwnerServiceCode(req.serviceCode());
                existing.setMaintainSource(req.maintainSource());
                existing.setUpdatedAt(now);
                existing.setUpdatedBy(operatorId);
                dependencyMapper.update(existing);
                updatedCount++;
            } else {
                ResourceDependency dep = new ResourceDependency();
                dep.setTenantId(tenantId);
                dep.setResourceEntityId(ri.sourceId());
                dep.setDependsOnResourceEntityId(ri.targetId());
                dep.setSourceOperationBits(ri.sourceBits());
                dep.setRequiredOperationBits(ri.requiredBits());
                dep.setAutoGrant(Boolean.TRUE.equals(ri.item().autoGrant()));
                dep.setDescription(ri.item().description());
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
            try {
                dependencyMapper.insertBatch(toInsert);
            } catch (DataIntegrityViolationException e) {
                if (isDependencyUniqueViolation(e)) {
                    throw new BizException(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode(),
                        PermissionErrorCode.DEPENDENCY_DUPLICATE.getMessage());
                }
                throw e;
            }
        }

        if (deletedCount == 0 && updatedCount == 0 && toInsert.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        OperationLogRuntimeContext.setSummary(
            "sync resource dependencies for service " + req.serviceCode()
                + ": inserted=" + toInsert.size()
                + ", updated=" + updatedCount
                + ", deleted=" + deletedCount
        );
    }

    /** 批量同步条目的预解析结果（资源 ID 与操作位已定） */
    private record ResolvedSyncItem(Long sourceId, Long targetId, Long sourceBits, Long requiredBits,
                                    DependencyBatchSyncReq.DependencySyncItem item) {}

    /** uk 语义三元组键（source + target + COALESCE(source_bits,0)） */
    private static String depKey(Long sourceId, Long targetId, Long sourceBits) {
        return sourceId + ":" + targetId + ":" + (sourceBits == null ? 0L : sourceBits);
    }

    /**
     * 构建操作位索引（资源类型编码 -> 操作码 -> binaryBit），循环外按类型批量解析。
     * 收集时过滤空白元素（与单条入口 resolveOperationBits 口径一致：混合空白忽略、
     * 全空白由 bitsOf 拒 20044）；未知操作码 fail-closed 20005（T-PERM-031 设计定案：
     * 静默丢弃会写出语义错误规则）。
     */
    private Map<String, Map<String, Long>> buildOperationBitIndex(Long tenantId, List<DependencyBatchSyncReq.DependencySyncItem> items) {
        Map<String, Set<String>> codesByType = new HashMap<>();
        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            if (hasCodes(item.sourceOperationCodes())) {
                codesByType.computeIfAbsent(item.sourceResourceTypeCode(), k -> new HashSet<>())
                    .addAll(nonBlankCodes(item.sourceOperationCodes()));
            }
            if (hasCodes(item.requiredOperationCodes())) {
                codesByType.computeIfAbsent(item.targetResourceTypeCode(), k -> new HashSet<>())
                    .addAll(nonBlankCodes(item.requiredOperationCodes()));
            }
        }
        Map<String, Map<String, Long>> index = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : codesByType.entrySet()) {
            if (entry.getValue().isEmpty()) {
                index.put(entry.getKey(), Map.of());
                continue;
            }
            index.put(entry.getKey(), resolveCodeBits(tenantId, entry.getKey(), entry.getValue()));
        }
        return index;
    }

    private static Set<String> nonBlankCodes(List<String> codes) {
        return codes.stream()
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
    }

    private static boolean hasCodes(List<String> codes) {
        return codes != null && !codes.isEmpty();
    }

    /** 全空白码列表拒绝 20044（三入口统一口径：混合空白忽略、全空白畸形参数） */
    private static void rejectBlankOnlyCodes(String resourceTypeCode) {
        throw new BizException(PermissionErrorCode.INVALID_PARAM.getCode(),
            "operationCodes 不能为全空白元素 (resourceType=" + resourceTypeCode + ")");
    }

    /** 从预解析索引取操作位（索引构建时已 fail-closed 校验；空白元素跳过，全空白拒 20044） */
    private static Long bitsOf(List<String> operationCodes, String resourceTypeCode,
                               Map<String, Map<String, Long>> opBitIndex) {
        if (!hasCodes(operationCodes)) return null;
        Map<String, Long> codeToBit = opBitIndex.getOrDefault(resourceTypeCode, Map.of());
        long bits = 0L;
        boolean anyCode = false;
        for (String code : operationCodes) {
            if (code == null || code.isBlank()) continue;
            anyCode = true;
            Long bit = codeToBit.get(code);
            if (bit == null) {
                throw new BizException(PermissionErrorCode.OPERATION_NOT_FOUND.getCode(),
                    "Operation permission not found: " + code + " (resourceType=" + resourceTypeCode + ")");
            }
            bits |= bit;
        }
        if (!anyCode) {
            rejectBlankOnlyCodes(resourceTypeCode);
        }
        return bits;
    }

    /**
     * 解析操作码为操作位掩码（fail-closed）
     * <p>
     * 将操作码列表转换为对应的二进制位掩码；空列表返回 null（source 侧=任意操作触发）。
     * 任一操作码解析不到即抛 20005 OPERATION_NOT_FOUND（T-PERM-031 设计定案：
     * 原实现静默丢弃未知码——部分丢弃合并已知位、全 miss 落 0，会写出语义错误规则
     * 且无任何报错）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param operationCodes   操作码列表
     * @param resourceTypeCode 资源类型编码
     * @return 操作位掩码，操作码列表为空返回 null
     * @throws BizException 操作码不存在（20005）时抛出
     */
    private Long resolveOperationBits(Long tenantId, List<String> operationCodes, String resourceTypeCode) {
        if (!hasCodes(operationCodes)) return null;
        Set<String> codeSet = nonBlankCodes(operationCodes);
        if (codeSet.isEmpty()) {
            // 全空白码列表视为畸形参数（required 侧 NOT NULL、source 侧语义未定义），显式拒绝
            rejectBlankOnlyCodes(resourceTypeCode);
        }
        return mergeBits(resolveCodeBits(tenantId, resourceTypeCode, codeSet));
    }

    /** 按类型批量解析操作码 -> binaryBit 映射（未知码 fail-closed 20005） */
    private Map<String, Long> resolveCodeBits(Long tenantId, String resourceTypeCode, Set<String> codeSet) {
        Map<String, Long> codeToIdMap = typeResolutionService.batchResolveOperationIds(tenantId, resourceTypeCode, codeSet);
        for (String code : codeSet) {
            if (codeToIdMap.get(code) == null) {
                throw new BizException(PermissionErrorCode.OPERATION_NOT_FOUND.getCode(),
                    "Operation permission not found: " + code + " (resourceType=" + resourceTypeCode + ")");
            }
        }
        Map<Long, OperationPermission> opMap = batchLoadOperations(tenantId, new HashSet<>(codeToIdMap.values()));
        Map<String, Long> codeToBit = new HashMap<>();
        for (Map.Entry<String, Long> entry : codeToIdMap.entrySet()) {
            OperationPermission op = opMap.get(entry.getValue());
            if (op == null || op.getBinaryBit() == null) {
                // 二次加载同样 fail-closed：code→id 解析与按 id 加载两查询间隙并发软删时拒绝，
                // 不得静默丢位（否则 create/update 会写入部分位或 0，重现要消除的语义降级）
                throw new BizException(PermissionErrorCode.OPERATION_NOT_FOUND.getCode(),
                    "Operation permission not found: " + entry.getKey() + " (resourceType=" + resourceTypeCode + ")");
            }
            codeToBit.put(entry.getKey(), op.getBinaryBit());
        }
        return codeToBit;
    }

    private static Long mergeBits(Map<String, Long> codeToBit) {
        long bits = 0L;
        for (Long bit : codeToBit.values()) {
            bits |= bit;
        }
        return bits;
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
            d.getAutoGrant(), d.getDescription(),
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
