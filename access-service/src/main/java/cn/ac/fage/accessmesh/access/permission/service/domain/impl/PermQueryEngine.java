package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper.BitMaskEntry;
import cn.ac.fage.accessmesh.access.permission.service.domain.*;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResolveContext;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.access.permission.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一权限查询引擎 -- 所有权限校验的唯一入口。
 *
 * <h3>查询流程</h3>
 * <ol>
 *   <li>解析用户角色（缓存L1->L2->DB）</li>
 *   <li>解析资源类型码和操作码为内部ID（批量）</li>
 *   <li>查询类型级权限（scopeAll=true）-- 1条SQL</li>
 *   <li>如果scopeAll匹配则提前返回</li>
 *   <li>查询实例级权限 -- 1条SQL</li>
 *   <li>评估条件和冲突（按参数标志）</li>
 *   <li>加载辅助实体（按参数标志）</li>
 *   <li>构建统一PermResult结果</li>
 * </ol>
 *
 * <h3>优化策略</h3>
 * <ul>
 *   <li>批量ID解析：避免N次单查询</li>
 *   <li>scopeAll优先匹配：匹配后跳过实例级查询</li>
 *   <li>内存筛选：matchesBit过滤、条件评估、冲突过滤</li>
 *   <li>操作权限缓存：通过CacheService缓存ID索引，优化位掩码计算效率</li>
 * </ul>
 */
@Component
public class PermQueryEngine {

    private static final Logger log = LoggerFactory.getLogger(PermQueryEngine.class);

    private final SubjectDomainService subjectDomainService;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermissionConditionDomainService conditionDomainService;
    private final PermissionConflictDomainService conflictDomainService;
    private final RolePermEntryMapper entryMapper;
    private final TypeResolutionService typeResolutionService;
    private final CacheService cacheService;
    private final OperationPermissionMapper operationPermissionMapper;

    /**
     * 构造函数注入依赖服务
     *
     * @param subjectDomainService      主体领域服务
     * @param rolePermMapper            角色权限映射器
     * @param resourceEntityMapper      资源实体数据访问层
     * @param abstractRoleMapper        抽象角色数据访问层
     * @param conditionDomainService    权限条件评估服务
     * @param conflictDomainService     权限冲突处理服务
     * @param entryMapper               权限条目映射器
     * @param typeResolutionService     类型解析服务
     * @param cacheService              统一缓存服务
     * @param operationPermissionMapper 操作权限数据访问层
     */
    public PermQueryEngine(SubjectDomainService subjectDomainService,
                           RoleResourcePermissionMapper rolePermMapper,
                           ResourceEntityMapper resourceEntityMapper,
                           AbstractRoleMapper abstractRoleMapper,
                           PermissionConditionDomainService conditionDomainService,
                           PermissionConflictDomainService conflictDomainService,
                           RolePermEntryMapper entryMapper,
                           TypeResolutionService typeResolutionService,
                           CacheService cacheService,
                           OperationPermissionMapper operationPermissionMapper) {
        this.subjectDomainService = subjectDomainService;
        this.rolePermMapper = rolePermMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.conditionDomainService = conditionDomainService;
        this.conflictDomainService = conflictDomainService;
        this.entryMapper = entryMapper;
        this.typeResolutionService = typeResolutionService;
        this.cacheService = cacheService;
        this.operationPermissionMapper = operationPermissionMapper;
    }

    /**
     * 执行统一权限查询
     *
     * @param q 权限查询参数
     * @return 权限查询结果
     */
    public PermResult query(PermQuery q) {
        // -- forUserView 分支 --
        if (q.forUserView()) {
            return queryForUserView(q);
        }

        // -- 0. 创建 ResolveContext，预解析所有类型 --
        ResolveContext ctx = new ResolveContext(q.tenantId(), typeResolutionService);
        if (q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()) {
            ctx.prepareResourceTypes(q.resourceTypeCodes());
        }
        if (q.operationCodes() != null && !q.operationCodes().isEmpty()
            && q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()) {
            ctx.prepareOperations(q.resourceTypeCodes(), q.operationCodes());
        }

        // -- 1. 解析角色 --
        Set<Long> roleIds = resolveRoleIds(q);
        if (roleIds.isEmpty()) {
            return PermResult.deny("NO_ROLE");
        }

        // -- 2. 解析资源类型（使用 ResolveContext）--
        Set<Integer> resourceTypes = resolveResourceTypes(q, ctx);

        // -- 3. 解析操作ID（使用 ResolveContext）--
        Set<Long> opIds = resolveOperationIds(q, ctx);
        Map<Integer, Long> bitMasks = resolveBitMasks(q.tenantId(), resourceTypes, opIds);

        // -- 4. 查询类型级权限（scopeAll=true） --
        List<RolePermEntry> scopeAllEntries = List.of();
        if (q.queryScopeAll() && !bitMasks.isEmpty()) {
            scopeAllEntries = queryScopeAll(q.tenantId(), roleIds, bitMasks);
        }

        // -- 5. scopeAll匹配时提前返回 --
        if (!scopeAllEntries.isEmpty() && q.earlyReturnOnScopeAll()) {
            scopeAllEntries = evaluateIfNeeded(q, scopeAllEntries);
            if (scopeAllEntries.isEmpty()) {
                return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
            }
            Map<String, Object> evalCtx = q.context();
            if (evalCtx == null) evalCtx = Map.of();
            PermResult.Builder builder = PermResult.builder(true, null)
                .scopeAllMatched(true)
                .scopeAllEntries(scopeAllEntries);
            loadAncillary(q, builder, scopeAllEntries, List.of(), roleIds);
            return builder.build();
        }

        // -- 6. 解析并查询实例级权限 --
        Set<Long> entityIds = resolveEntityIds(q);
        List<RolePermEntry> instanceEntries = List.of();

        if (q.queryInstance() && !entityIds.isEmpty()) {
            instanceEntries = queryInstance(q.tenantId(), roleIds, entityIds, bitMasks);
        }

        // -- 6.5 根据继承模式展开资源 --
        if (q.queryInstance() && (q.inheritParents() || q.inheritChildren()) && !instanceEntries.isEmpty()) {
            instanceEntries = expandByInheritMode(q.tenantId(), instanceEntries,
                q.inheritParents(), q.inheritChildren());
        }

        // -- 7. 合并scopeAll和实例级结果 --
        List<RolePermEntry> combined = new ArrayList<>(scopeAllEntries);
        combined.addAll(instanceEntries);
        if (combined.isEmpty()) {
            return PermResult.deny("NO_PERMISSION");
        }

        // 评估条件和冲突
        combined = evaluateIfNeeded(q, combined);
        if (combined.isEmpty()) {
            return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
        }

        PermResult.Builder builder = PermResult.builder(true, null)
            .scopeAllMatched(!scopeAllEntries.isEmpty())
            .scopeAllEntries(scopeAllEntries)
            .instanceEntries(instanceEntries);
        loadAncillary(q, builder, scopeAllEntries, instanceEntries, roleIds);
        return builder.build();
    }

    // ===== 引擎便捷 API（T-ACCESS-016 定稿终态，T-PERM-042 落地）=====
    // code 与 entityId 两轨不得混用：
    //   - hasPermissionByCode / getDeniedResourceCodes：业务编码语义，对外（USER/ROLE 等业务对象门禁与跨服务 SDK）。
    //     resource_entity(USER).code = subjectId、resource_entity(ROLE).code = roleId（architecture §12.3）。
    //   - hasPermissionByEntityId / getDeniedEntityIds：resource_entity.id 语义，仅引擎内部或已完成解析的调用方
    //     （资源树、API 映射、资源依赖、权限树等直接管理资源实体的后台链路）。
    // 引擎纯查询不抛 SecurityException；异常由调用方显式抛出（admin 域经 AdminPermissionValidator 门面、
    // permission 域 AppService if-throw）。

    /**
     * 按业务编码检查是否有权限（对外）
     * <p>
     * 主体参数即主体 ID（T-ORG-001 统一后 {@code operatorId = abstract_user.id = sys_user.id}），
     * 无任何运行时 ID 空间转换层。
     * {@code resourceCode} 为业务编码（USER/ROLE 门禁传主体/角色 ID 字符串化，SERVICE 传 serviceCode）；
     * {@code null} 表示仅类型级校验。未知类型/未知操作 fail-closed 拒绝。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceCode     业务编码，null 表示类型级校验
     * @param operationCode    操作码
     * @return 是否有权限
     */
    public boolean hasPermissionByCode(Long tenantId, Long subjectId, String resourceTypeCode,
                                        String resourceCode, String operationCode) {
        PermQuery q = PermQuery.forValidate(tenantId, subjectId, resourceTypeCode, resourceCode, operationCode);
        return query(q).allowed();
    }

    /**
     * 按 resource_entity.id 检查是否有权限（仅引擎内部或已完成解析的调用方）
     * <p>
     * 实例目标直接以 {@code resource_entity.id} 匹配，不做 code 解析。
     * 仅限权限域内部及直接管理资源实体的后台链路（资源树、API 映射、资源依赖、权限树等）使用；
     * USER/ROLE 等业务对象门禁与跨服务 SDK 禁止使用（统一业务编码，见
     * {@link #hasPermissionByCode}）。门禁主体契约同 {@link #hasPermissionByCode}。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceEntityId resource_entity.id，null 表示类型级校验
     * @param operationCode    操作码
     * @return 是否有权限
     */
    public boolean hasPermissionByEntityId(Long tenantId, Long subjectId, String resourceTypeCode,
                                            Long resourceEntityId, String operationCode) {
        PermQuery q = PermQuery.forValidateByEntityId(
            tenantId, subjectId, resourceTypeCode, resourceEntityId, operationCode);
        return query(q).allowed();
    }

    /**
     * 批量获取被拒绝的业务编码集合（对外，纯查询不抛异常）
     * <p>
     * 门禁主体契约同 {@link #hasPermissionByCode}。优化管线（implementation §3.1）：
     * 一次解析用户角色 → 一次查询类型级 scopeAll（命中且条件/冲突评估通过则全部允许，
     * <b>包括尚无投影实体的编码</b>——与 {@link #hasPermissionByCode} 的 scopeAll 提前返回
     * 语义一致）→ 未命中才一次批量 {@code code → resource_entity.id} 解析
     * （{@link TypeResolutionService#batchResolveResourceIds}，无 N+1）+ 一次批量实例级查询 →
     * 内存计算拒绝集合。未解析到投影实体的 code 直接拒绝（fail-closed，与单条 forAuthCheck
     * 内部解析语义一致）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceCodes    业务编码集合
     * @param operationCode    操作码
     * @return 被拒绝的业务编码集合
     */
    public Set<String> getDeniedResourceCodes(Long tenantId, Long subjectId, String resourceTypeCode,
                                               Set<String> resourceCodes, String operationCode) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> distinctCodes = new LinkedHashSet<>(resourceCodes);

        // 1. 解析用户角色（1次查询）
        Set<Long> roleIds = subjectDomainService.resolveEffectiveRoles(tenantId, subjectId);
        if (roleIds.isEmpty()) {
            return distinctCodes; // 无角色 = 全部拒绝
        }

        // 2. 预解析类型和操作ID；未知类型/未知操作 fail-closed
        ResolveContext ctx = new ResolveContext(tenantId, typeResolutionService);
        ctx.prepareResourceTypes(Set.of(resourceTypeCode));
        ctx.prepareOperations(resourceTypeCode, Set.of(operationCode));
        Integer resourceTypeValue = ctx.getResourceTypeValue(resourceTypeCode);
        Long operationId = ctx.getOperationId(resourceTypeCode, operationCode);
        if (resourceTypeValue == null || operationId == null) {
            return distinctCodes; // 未知类型/未知操作 = 全部拒绝
        }

        // 3. 类型级 scopeAll 优先（含条件与冲突评估）：命中则全部允许，不做 code 解析
        Map<Integer, Long> bitMasks = resolveBitMasks(tenantId, Set.of(resourceTypeValue), Set.of(operationId));
        if (passesScopeAll(tenantId, roleIds, bitMasks)) {
            return Set.of();
        }

        // 4. 未命中才批量 code → entity 解析（TypeResolutionService 下沉，禁 N+1）
        List<ResourceResolveRequest> requests = distinctCodes.stream()
            .map(code -> new ResourceResolveRequest(resourceTypeCode, code, null, null))
            .toList();
        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(tenantId, requests);
        Map<String, Long> entityIdByCode = new LinkedHashMap<>();
        for (String code : distinctCodes) {
            Long entityId = resolved.get(new ResourceResolveKey(resourceTypeCode, code, null, null));
            if (entityId != null) {
                entityIdByCode.put(code, entityId);
            }
        }
        if (entityIdByCode.isEmpty()) {
            return distinctCodes; // 无有效投影 = 全部拒绝
        }

        // 5. 实例级批量查询（含条件与冲突评估）+ 内存映射回编码；未解析的 code 一并拒绝（fail-closed）
        Set<Long> deniedEntityIds = computeInstanceDenied(
            tenantId, roleIds, new LinkedHashSet<>(entityIdByCode.values()), bitMasks);
        Set<String> denied = new LinkedHashSet<>();
        for (String code : distinctCodes) {
            Long entityId = entityIdByCode.get(code);
            if (entityId == null || deniedEntityIds.contains(entityId)) {
                denied.add(code);
            }
        }
        return denied;
    }

    /**
     * 批量获取被拒绝的 resource_entity.id 集合（仅引擎内部或已完成解析的调用方，纯查询不抛异常）
     * <p>
     * 实例目标直接按 {@code resource_entity.id} 匹配，不做 code 解析；使用边界同
     * {@link #hasPermissionByEntityId}。门禁主体契约同 {@link #hasPermissionByCode}。
     * 未知类型/未知操作/无角色 fail-closed 全量拒绝。相比 N 次单独查询：
     * 一次解析用户角色 → 一次类型级 scopeAll 查询（命中则全部允许，含条件与冲突评估）→
     * 一次批量实例级查询（含条件与冲突评估）→ 内存计算拒绝集合。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceEntityIds resource_entity.id 集合
     * @param operationCode    操作码
     * @return 被拒绝的 resource_entity.id 集合
     */
    public Set<Long> getDeniedEntityIds(Long tenantId, Long subjectId, String resourceTypeCode,
                                         Set<Long> resourceEntityIds, String operationCode) {
        if (resourceEntityIds == null || resourceEntityIds.isEmpty()) {
            return Set.of();
        }

        // 1. 解析用户角色（1次查询）
        Set<Long> roleIds = subjectDomainService.resolveEffectiveRoles(tenantId, subjectId);
        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>(resourceEntityIds); // 无角色 = 全部拒绝
        }

        // 2. 预解析类型和操作ID（避免重复调用）
        ResolveContext ctx = new ResolveContext(tenantId, typeResolutionService);
        ctx.prepareResourceTypes(Set.of(resourceTypeCode));
        ctx.prepareOperations(resourceTypeCode, Set.of(operationCode));

        Integer resourceTypeValue = ctx.getResourceTypeValue(resourceTypeCode);
        if (resourceTypeValue == null) {
            return new LinkedHashSet<>(resourceEntityIds); // 未知类型 = 全部拒绝
        }
        Long operationId = ctx.getOperationId(resourceTypeCode, operationCode);
        if (operationId == null) {
            return new LinkedHashSet<>(resourceEntityIds); // 未知操作 = 全部拒绝
        }

        // 3. 类型级 scopeAll 优先（含条件与冲突评估）：命中则全部允许
        Map<Integer, Long> bitMasks = resolveBitMasks(tenantId, Set.of(resourceTypeValue), Set.of(operationId));
        if (passesScopeAll(tenantId, roleIds, bitMasks)) {
            return Set.of();
        }

        // 4. 实例级批量查询（含条件与冲突评估）+ 内存计算拒绝集合
        return computeInstanceDenied(tenantId, roleIds, resourceEntityIds, bitMasks);
    }

    /**
     * 类型级 scopeAll 是否放行（含条件与冲突评估）。
     * <p>
     * scopeAll 命中且评估非空即放行该资源类型的任意实例；条件或冲突评估清空条目则视为未命中。
     * </p>
     */
    private boolean passesScopeAll(Long tenantId, Set<Long> roleIds, Map<Integer, Long> bitMasks) {
        List<RolePermEntry> scopeAllEntries = queryScopeAll(tenantId, roleIds, bitMasks);
        if (scopeAllEntries.isEmpty()) {
            return false;
        }
        List<RolePermEntry> evaluated = conditionDomainService.evaluate(tenantId, scopeAllEntries, Map.of());
        if (evaluated.isEmpty()) {
            return false;
        }
        evaluated = conflictDomainService.filterPermMutex(tenantId, evaluated);
        return !evaluated.isEmpty();
    }

    /**
     * 实例级批量查询（含条件与冲突评估）并计算拒绝集合。
     * <p>
     * 调用方须已完成角色/类型/操作解析与 scopeAll 检查（未命中路径的共享实例步骤）。
     * </p>
     */
    private Set<Long> computeInstanceDenied(Long tenantId, Set<Long> roleIds,
                                             Set<Long> resourceEntityIds, Map<Integer, Long> bitMasks) {
        List<RolePermEntry> instanceEntries = queryInstance(tenantId, roleIds, resourceEntityIds, bitMasks);

        if (!instanceEntries.isEmpty()) {
            instanceEntries = conditionDomainService.evaluate(tenantId, instanceEntries, Map.of());
        }
        if (!instanceEntries.isEmpty()) {
            instanceEntries = conflictDomainService.filterPermMutex(tenantId, instanceEntries);
        }

        Set<Long> allowedEntityIds = new HashSet<>();
        for (RolePermEntry entry : instanceEntries) {
            if (entry.resourceEntityId() != null) {
                allowedEntityIds.add(entry.resourceEntityId());
            }
        }

        Set<Long> denied = new LinkedHashSet<>();
        for (Long entityId : resourceEntityIds) {
            if (entityId == null || !allowedEntityIds.contains(entityId)) {
                denied.add(entityId);
            }
        }
        return denied;
    }

    // ===== 私有步骤方法 =====

    /**
     * 解析查询参数中的角色ID
     */
    private Set<Long> resolveRoleIds(PermQuery q) {
        if (q.roleIds() != null && !q.roleIds().isEmpty()) {
            return q.roleIds();
        }
        if (q.userId() == null) {
            return Set.of();
        }
        if (q.useRoleCache()) {
            return subjectDomainService.resolveEffectiveRoles(q.tenantId(), q.userId());
        }
        // 绕过缓存，直接批量解析
        Map<Long, Set<Long>> batch = subjectDomainService.batchResolveEffectiveRoles(
            q.tenantId(), Set.of(q.userId()));
        return batch.getOrDefault(q.userId(), Set.of());
    }

    /**
     * 解析查询参数中的资源类型值（使用 ResolveContext）
     */
    private Set<Integer> resolveResourceTypes(PermQuery q, ResolveContext ctx) {
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        return new HashSet<>(ctx.getResourceTypeValues(q.resourceTypeCodes()).values());
    }

    /**
     * 解析查询参数中的操作ID（使用 ResolveContext）
     */
    private Set<Long> resolveOperationIds(PermQuery q, ResolveContext ctx) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        return ctx.getOperationIds(q.resourceTypeCodes(), q.operationCodes());
    }

    /**
     * 解析查询参数中的操作ID（旧方法，保留兼容）
     */
    private Set<Long> resolveOperationIds(PermQuery q) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
        // 批量解析：遍历所有resourceTypeCode，合并结果
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        Set<Long> result = new HashSet<>();
        for (String rtCode : q.resourceTypeCodes()) {
            Map<String, Long> map = typeResolutionService.batchResolveOperationIds(
                q.tenantId(), rtCode, q.operationCodes());
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 解析查询参数中的资源实体ID
     */
    private Set<Long> resolveEntityIds(PermQuery q) {
        if (q.resourceEntityIds() != null && !q.resourceEntityIds().isEmpty()) {
            return q.resourceEntityIds();
        }
        if (q.resourceCodes() == null || q.resourceCodes().isEmpty()) return Set.of();
        // 批量解析：遍历所有resourceTypeCode，合并结果
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();

        Set<Long> allResolved = new HashSet<>();
        for (String rtCode : q.resourceTypeCodes()) {
            List<ResourceResolveRequest> requests = q.resourceCodes().stream()
                .map(code -> new ResourceResolveRequest(rtCode, code, q.codeType(), q.domainCode()))
                .toList();

            Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(q.tenantId(), requests);
            allResolved.addAll(resolved.values());
        }

        return allResolved;
    }

    /**
     * 查询类型级权限（scopeAll=true）
     * <p>
     * 使用单次SQL批量查询多个资源类型的位掩码条件，避免多次SQL调用。
     * </p>
     */
    private List<RolePermEntry> queryScopeAll(Long tenantId, Set<Long> roleIds,
                                               Map<Integer, Long> bitMasks) {
        if (bitMasks == null || bitMasks.isEmpty()) {
            return List.of();
        }
        // 构建 BitMaskEntry 列表
        List<BitMaskEntry> entries = bitMasks.entrySet().stream()
            .map(e -> new BitMaskEntry(e.getKey(), e.getValue()))
            .toList();
        // 单次SQL查询
        return rolePermMapper.selectScopeAllPermsByBitsBatch(tenantId, roleIds, entries)
            .stream()
            .map(entryMapper::toEntry)
            .toList();
    }

    /**
     * 查询实例级权限
     * <p>
     * 使用单次SQL批量查询多个资源类型的位掩码条件，避免多次SQL调用。
     * </p>
     */
    private List<RolePermEntry> queryInstance(Long tenantId, Set<Long> roleIds,
                                               Set<Long> entityIds, Map<Integer, Long> bitMasks) {
        if (bitMasks == null || bitMasks.isEmpty()) {
            return List.of();
        }
        // 构建 BitMaskEntry 列表
        List<BitMaskEntry> entries = bitMasks.entrySet().stream()
            .map(e -> new BitMaskEntry(e.getKey(), e.getValue()))
            .toList();
        // 单次SQL查询
        return rolePermMapper.selectInstancePermsByBitsBatch(tenantId, roleIds, entityIds, entries)
            .stream()
            .map(entryMapper::toEntry)
            .toList();
    }

    /**
     * 按需评估条件和冲突
     */
    private List<RolePermEntry> evaluateIfNeeded(PermQuery q, List<RolePermEntry> entries) {
        if (entries.isEmpty()) return entries;
        Map<String, Object> ctx = q.context();
        if (ctx == null) ctx = Map.of();
        if (q.evaluateConditions() && !q.markConditionsOnly()) {
            // T-PERM-017 C3：markConditionsOnly=true 时跳过条件过滤，
            // 条件条目原样保留，由调用方（SnapshotAssembler/Gateway）决定下发与重评。
            entries = conditionDomainService.evaluate(q.tenantId(), entries, ctx);
        }
        if (q.evaluateConflicts() && !entries.isEmpty()) {
            entries = conflictDomainService.filterPermMutex(q.tenantId(), entries);
        }
        return entries;
    }

    /**
     * 加载辅助实体（资源、操作、角色）
     */
    private void loadAncillary(PermQuery q, PermResult.Builder builder,
                                List<RolePermEntry> scopeAll, List<RolePermEntry> instance,
                                Set<Long> roleIds) {
        Set<Long> allEntityIds = new HashSet<>();
        Set<Long> targetOpIds = resolveOperationIds(q);
        Map<Integer, Set<Long>> grantedBitsByType = new LinkedHashMap<>();
        List<RolePermEntry> entries = Stream.concat(scopeAll.stream(), instance.stream()).toList();
        entries.forEach(e -> {
            if (e.resourceEntityId() != null) allEntityIds.add(e.resourceEntityId());
            if (e.resourceType() != null && e.grantedBits() != null) {
                grantedBitsByType.computeIfAbsent(e.resourceType(), _unused -> new LinkedHashSet<>()).add(e.grantedBits());
            }
        });
        // 同时包含查询参数中的resourceEntityIds（scopeAll权限的entityId可能为null）
        if (q.resourceEntityIds() != null) allEntityIds.addAll(q.resourceEntityIds());

        if (q.includeResources()) {
            builder.resourceMap(batchLoadResources(q.tenantId(), allEntityIds));
        }
        Map<Integer, List<OperationPermission>> operationsByType = Map.of();
        if (q.includeOperations()) {
            Map<Long, OperationPermission> operationMap = new LinkedHashMap<>(batchLoadOperations(q.tenantId(), targetOpIds));
            operationsByType = batchLoadOperationsByResourceTypes(
                q.tenantId(),
                grantedBitsByType.keySet()
            );
            for (Map.Entry<Integer, Set<Long>> entry : grantedBitsByType.entrySet()) {
                for (OperationPermission operation : operationsByType.getOrDefault(entry.getKey(), List.of())) {
                    if (q.evaluateMatchesBit() || entry.getValue().contains(operation.getBinaryBit())) {
                        operationMap.put(operation.getId(), operation);
                    }
                }
            }
            builder.operationMap(operationMap);
        }
        if (shouldBuildEffectiveOperationEntries(q)) {
            if (operationsByType.isEmpty()) {
                operationsByType = batchLoadOperationsByResourceTypes(q.tenantId(), grantedBitsByType.keySet());
            }
            builder.effectiveOperationEntries(buildEffectiveOperationEntries(entries, operationsByType));
        }
        if (q.includeRoles() && roleIds != null && !roleIds.isEmpty()) {
            builder.roleMap(batchLoadRoles(q.tenantId(), roleIds));
        }
    }

    private boolean shouldBuildEffectiveOperationEntries(PermQuery q) {
        return q.evaluateMatchesBit() && q.includeOperations();
    }

    private List<PermResult.EffectiveOperationEntry> buildEffectiveOperationEntries(
        List<RolePermEntry> entries,
        Map<Integer, List<OperationPermission>> operationsByType) {
        if (entries == null || entries.isEmpty() || operationsByType == null || operationsByType.isEmpty()) {
            return List.of();
        }

        Map<String, PermResult.EffectiveOperationEntry> result = new LinkedHashMap<>();
        for (RolePermEntry entry : entries) {
            if (entry.resourceType() == null || entry.grantedBits() == null) {
                continue;
            }
            List<OperationPermission> operations = operationsByType.getOrDefault(entry.resourceType(), List.of());
            OperationPermission granted = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                operations, entry.resourceType(), entry.grantedBits());
            if (granted == null) {
                continue;
            }
            for (OperationPermission covered : OperationPermissionUtils.coveredOperations(granted, operations)) {
                if (covered.getCode() == null || covered.getBinaryBit() == null) {
                    continue;
                }
                PermResult.EffectiveOperationEntry projection = new PermResult.EffectiveOperationEntry(
                    entry.permissionId(),
                    entry.roleId(),
                    entry.resourceEntityId(),
                    entry.resourceType(),
                    entry.grantedBits(),
                    granted.getCode(),
                    OperationPermissionUtils.effectiveBits(granted),
                    covered.getCode(),
                    covered.getBinaryBit(),
                    entry.grantSource(),
                    entry.scopeAll()
                );
                result.putIfAbsent(effectiveOperationKey(projection), projection);
            }
        }
        return List.copyOf(result.values());
    }

    private String effectiveOperationKey(PermResult.EffectiveOperationEntry entry) {
        return entry.permissionId() + "|"
            + entry.roleId() + "|"
            + entry.resourceEntityId() + "|"
            + entry.resourceType() + "|"
            + entry.operationBinaryBit() + "|"
            + entry.scopeAll();
    }

    /**
     * 解析位掩码映射
     * <p>
     * 为每个资源类型计算位掩码，用于SQL位操作查询。
     * 使用 CacheService 缓存操作权限（按ID索引），优化查找效率。
     * </p>
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @param opIds         操作权限ID集合
     * @return resourceType → bitMask 映射
     */
    private Map<Integer, Long> resolveBitMasks(Long tenantId, Set<Integer> resourceTypes, Set<Long> opIds) {
        if (resourceTypes == null || resourceTypes.isEmpty() || opIds == null || opIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OperationPermission> targetOps = batchLoadOperations(tenantId, opIds);
        if (targetOps.isEmpty()) {
            return Map.of();
        }

        // 操作位空间按类型完全隔离（全局操作概念已退役，2026-08-30 设计定案）：
        // 每类型有效操作集 = 该类型专属操作，uk_operation_permission_typed_bit 保证
        // 同类型同位不异码。冷缓存批量回源：getBatch 收集 miss 类型后一次批量专属
        // 查询（IN），putBatch 分组回填——不逐类型单查。
        Set<String> cacheKeys = new LinkedHashSet<>();
        for (Integer resourceType : resourceTypes) {
            cacheKeys.add(PermCacheCatalog.operationPermissionsByTypeKey(resourceType));
        }
        Map<String, Map<Long, OperationPermission>> opMapsByCacheKey = new LinkedHashMap<>(cacheService.getBatch(
            PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKeys));
        List<Integer> missTypes = new ArrayList<>();
        for (Integer resourceType : resourceTypes) {
            if (!opMapsByCacheKey.containsKey(PermCacheCatalog.operationPermissionsByTypeKey(resourceType))) {
                missTypes.add(resourceType);
            }
        }
        if (!missTypes.isEmpty()) {
            Map<String, Map<Long, OperationPermission>> toPut = new LinkedHashMap<>();
            for (OperationPermission specific : operationPermissionMapper.selectByTenantAndResourceTypes(
                tenantId, new LinkedHashSet<>(missTypes))) {
                if (specific.getResourceType() == null) {
                    continue;
                }
                toPut.computeIfAbsent(PermCacheCatalog.operationPermissionsByTypeKey(specific.getResourceType()),
                        key -> new LinkedHashMap<>())
                    .put(specific.getId(), specific);
            }
            if (!toPut.isEmpty()) {
                cacheService.putBatch(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, toPut);
            }
            opMapsByCacheKey.putAll(toPut);
        }

        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            Map<Long, OperationPermission> opMap = opMapsByCacheKey.get(
                PermCacheCatalog.operationPermissionsByTypeKey(resourceType));
            if (opMap == null || opMap.isEmpty()) {
                continue;
            }

            long mask = 0L;
            for (OperationPermission targetOp : targetOps.values()) {
                // 目标操作只在本类型位空间参与判定（多类型查询跳过其他类型的专属操作）
                if (targetOp.getResourceType() != null
                    && !Objects.equals(resourceType, targetOp.getResourceType())) {
                    continue;
                }
                mask |= OperationPermissionUtils.computeCoveringBitMask(opMap.values(), targetOp.getBinaryBit());
            }
            if (mask != 0L) {
                result.put(resourceType, mask);
            }
        }
        return result;
    }

    // ===== 私有批量加载方法（替代 EntityBatchLoadDomainService） =====

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

    /**
     * 按资源类型批量加载操作权限
     */
    private Map<Integer, List<OperationPermission>> batchLoadOperationsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<OperationPermission>> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            if (resourceType == null) {
                continue;
            }
            result.put(resourceType, operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType));
        }
        return result;
    }

    /**
     * 批量加载资源实体
     */
    private Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return resourceEntityMapper.selectValidByIds(tenantId, ids)
            .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));
    }

    /**
     * 批量加载抽象角色
     */
    private Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return abstractRoleMapper.selectValidByIds(tenantId, ids)
            .stream().collect(Collectors.toMap(AbstractRole::getId, role -> role, (a, b) -> a));
    }

    // ===== 资源继承展开 =====

    /**
     * 根据继承模式展开实例级权限条目
     * <p>
     * 加载全部有效资源构建父子关系图，然后：
     * <ul>
     *   <li>inheritParents时：向上遍历父链，为每个祖先资源克隆权限条目</li>
     *   <li>inheritChildren时：向下递归收集所有子孙，为每个后代资源克隆权限条目</li>
     * </ul>
     * 克隆条目的grantSource设为"INHERITED"，其他字段保持不变。
     * scopeAll条目（resourceEntityId为null）不参与展开。
     * </p>
     *
     * @param tenantId         租户ID
     * @param entries          原始实例级权限条目
     * @param inheritParents   是否继承父资源权限
     * @param inheritChildren  是否继承子资源权限
     * @return 合并后的权限条目列表（原条目 + 继承条目）
     */
    private List<RolePermEntry> expandByInheritMode(Long tenantId, List<RolePermEntry> entries,
                                                      boolean inheritParents, boolean inheritChildren) {
        // 收集有资源实体ID的条目
        Set<Long> entityIds = entries.stream()
            .map(RolePermEntry::resourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (entityIds.isEmpty()) {
            return entries;
        }

        // 加载全部有效资源（用于构建完整的父子关系图）
        List<ResourceEntity> allResources = resourceEntityMapper.selectAllValid(tenantId);
        if (allResources.isEmpty()) {
            return entries;
        }

        // 构建资源映射和父子关系图
        Map<Long, ResourceEntity> idToResource = allResources.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r, (a, b) -> a));
        Map<Long, List<Long>> parentIdToChildren = new LinkedHashMap<>();
        Map<Long, Long> idToParentId = new LinkedHashMap<>();
        for (ResourceEntity resource : allResources) {
            if (resource.getParentId() != null && resource.getParentId() != 0L) {
                parentIdToChildren.computeIfAbsent(resource.getParentId(), _unused -> new ArrayList<>())
                    .add(resource.getId());
                idToParentId.put(resource.getId(), resource.getParentId());
            }
        }

        // 收集继承条目
        List<RolePermEntry> inheritedEntries = new ArrayList<>();
        Set<String> inheritedKeys = new HashSet<>(); // 用于去重：entityId+"|"+permissionId

        for (RolePermEntry entry : entries) {
            Long srcEntityId = entry.resourceEntityId();
            if (srcEntityId == null) {
                continue; // scopeAll条目不参与展开
            }

            if (inheritChildren) {
                Set<Long> descendants = new LinkedHashSet<>();
                collectDescendants(srcEntityId, parentIdToChildren, descendants);
                for (Long descId : descendants) {
                    String key = descId + "|" + entry.permissionId();
                    if (inheritedKeys.add(key)) {
                        inheritedEntries.add(cloneWithInherited(entry, descId));
                    }
                }
            }

            if (inheritParents) {
                Long current = idToParentId.get(srcEntityId);
                while (current != null) {
                    String key = current + "|" + entry.permissionId();
                    if (inheritedKeys.add(key)) {
                        inheritedEntries.add(cloneWithInherited(entry, current));
                    }
                    current = idToParentId.get(current);
                }
            }
        }

        // 合并原始条目和继承条目
        List<RolePermEntry> result = new ArrayList<>(entries);
        result.addAll(inheritedEntries);
        return result;
    }

    /**
     * 递归收集所有后代资源ID
     *
     * @param parentId      父资源ID
     * @param childrenMap   父ID→子ID列表映射
     * @param result        收集结果的集合
     */
    private void collectDescendants(Long parentId, Map<Long, List<Long>> childrenMap, Set<Long> result) {
        List<Long> children = childrenMap.getOrDefault(parentId, List.of());
        for (Long childId : children) {
            if (result.add(childId)) {
                collectDescendants(childId, childrenMap, result);
            }
        }
    }

    /**
     * 克隆权限条目，将grantSource设为"INHERITED"，resourceEntityId设为目标实体ID
     *
     * @param source       原始权限条目
     * @param targetEntityId 目标资源实体ID
     * @return 克隆后的权限条目
     */
    private RolePermEntry cloneWithInherited(RolePermEntry source, Long targetEntityId) {
        return new RolePermEntry(
            source.permissionId(),
            source.roleId(),
            targetEntityId,
            source.resourceCode(),
            source.resourceType(),
            source.grantedBits(),
            source.operationCode(),
            source.effectiveBits(),
            "INHERITED",
            source.canGrant(),
            source.conditionId(),
            source.hasCondition(),
            source.dependOn(),
            source.scopeAll()
        );
    }

    // ===== forUserView 专用方法 =====

    /**
     * 执行用户视图查询
     * <p>
     * 查询该用户全部角色权限记录，不按资源类型/操作码/位掩码过滤。
     * 查询流程：
     * <ol>
     *   <li>解析用户角色</li>
     *   <li>查询全部角色权限记录（scopeAll + instance）</li>
     *   <li>评估条件和冲突（按参数标志）</li>
     *   <li>加载辅助实体（资源、操作、角色）</li>
     *   <li>构建结果</li>
     * </ol>
     * </p>
     *
     * @param q 权限查询参数（forUserView=true）
     * @return 权限查询结果
     */
    private PermResult queryForUserView(PermQuery q) {
        // 1. 解析角色
        Set<Long> roleIds = resolveRoleIds(q);
        if (roleIds.isEmpty()) {
            return PermResult.deny("NO_ROLE");
        }

        // 2. 查询全部角色权限（scopeAll + instance）—— T-PERM-018 激活 ROLE_PERM_SNAPSHOT 读缓存
        List<RolePermEntry> allEntries = loadRolePermEntriesWithCache(q.tenantId(), roleIds);

        if (allEntries.isEmpty()) {
            return PermResult.deny("NO_PERMISSION");
        }

        // 3. 评估条件和冲突
        allEntries = evaluateIfNeeded(q, allEntries);
        if (allEntries.isEmpty()) {
            return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
        }

        // 4. 构建结果（forUserView 不做 scopeAll/instance 语义断言，全部归入实例条目）
        PermResult.Builder builder = PermResult.builder(true, null)
            .scopeAllMatched(false)
            .scopeAllEntries(List.of())
            .instanceEntries(allEntries);

        // 5. 加载辅助实体（资源、操作、角色）
        loadAncillaryForView(q, builder, allEntries, roleIds);

        return builder.build();
    }

    /**
     * 加载角色权限条目（带 ROLE_PERM_SNAPSHOT 读缓存）。
     * <p>
     * T-PERM-018 缓存下沉：getBatch 批量查 roleIds，miss 集合 1 SQL（selectValidByRoleIds），
     * putBatch 回填；空权限角色缓存空列表（List.of()，非 null）防穿透。
     * 缓存值为条件评估前、互斥过滤前的原始权限记录；条件实时评估（条件变更洞消失）。
     * T-ACCESS-008：授权 L2 miss——SQL 前记录单调时钟起点（beginRead），
     * 回填只写剩余 TTL；批量共享同一起点，不得重置。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 全部角色的权限条目（合并，可变列表）
     */
    private List<RolePermEntry> loadRolePermEntriesWithCache(Long tenantId, Set<Long> roleIds) {
        // 1. 批量读缓存
        Map<Long, List<RolePermEntry>> cached = cacheService.getBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleIds);
        List<RolePermEntry> allEntries = new ArrayList<>();
        Set<Long> miss = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            List<RolePermEntry> entries = cached.get(roleId);
            if (entries != null) {
                // 命中（含空列表缓存，防穿透）
                allEntries.addAll(entries);
            } else {
                miss.add(roleId);
            }
        }

        // 2. miss 集合回源 1 SQL（读取起点在 SQL 前——剩余 TTL 从此刻起算）
        if (!miss.isEmpty()) {
            CacheReadToken<List<RolePermEntry>> readToken =
                cacheService.beginRead(PermCacheCatalog.ROLE_PERM_SNAPSHOT);
            List<RoleResourcePermission> dbRows = rolePermMapper.selectValidByRoleIds(tenantId, miss);
            Map<Long, List<RolePermEntry>> perRole = new LinkedHashMap<>();
            for (RoleResourcePermission p : dbRows) {
                perRole.computeIfAbsent(p.getAbstractRoleId(), k -> new ArrayList<>()).add(entryMapper.toEntry(p));
            }

            Map<Long, List<RolePermEntry>> toPut = new HashMap<>();
            for (Long roleId : miss) {
                List<RolePermEntry> entries = perRole.getOrDefault(roleId, List.of());
                allEntries.addAll(entries);
                toPut.put(roleId, entries); // 空列表（List.of()）也缓存，防穿透
            }
            cacheService.putBatch(readToken, tenantId, toPut);
        }

        return allEntries;
    }

    /**
     * 为视图查询加载辅助实体（资源、操作、角色）
     * <p>
     * 从权限条目中提取所有关联的资源实体ID、资源类型，
     * 批量加载对应的资源、操作权限和角色信息。
     * </p>
     *
     * @param q      权限查询参数
     * @param builder 结果构建器
     * @param entries 权限条目列表
     * @param roleIds 角色ID集合
     */
    private void loadAncillaryForView(PermQuery q, PermResult.Builder builder,
                                       List<RolePermEntry> entries, Set<Long> roleIds) {
        Set<Long> allEntityIds = new HashSet<>();
        for (RolePermEntry e : entries) {
            if (e.resourceEntityId() != null) {
                allEntityIds.add(e.resourceEntityId());
            }
        }

        if (q.includeResources()) {
            builder.resourceMap(batchLoadResources(q.tenantId(), allEntityIds));
        }
        Map<Integer, List<OperationPermission>> operationsByType = Map.of();
        if (q.includeOperations()) {
            // 按所有资源类型批量加载操作权限
            Set<Integer> allResourceTypes = entries.stream()
                .map(RolePermEntry::resourceType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            operationsByType = batchLoadOperationsByResourceTypes(q.tenantId(), allResourceTypes);
            Map<Long, OperationPermission> opMap = new LinkedHashMap<>();
            for (List<OperationPermission> operations : operationsByType.values()) {
                for (OperationPermission op : operations) {
                    opMap.put(op.getId(), op);
                }
            }
            builder.operationMap(opMap);
        }
        if (shouldBuildEffectiveOperationEntries(q)) {
            if (operationsByType.isEmpty()) {
                Set<Integer> allResourceTypes = entries.stream()
                    .map(RolePermEntry::resourceType)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                operationsByType = batchLoadOperationsByResourceTypes(q.tenantId(), allResourceTypes);
            }
            builder.effectiveOperationEntries(buildEffectiveOperationEntries(entries, operationsByType));
        }
        if (q.includeRoles() && roleIds != null && !roleIds.isEmpty()) {
            builder.roleMap(batchLoadRoles(q.tenantId(), roleIds));
        }
    }
}
