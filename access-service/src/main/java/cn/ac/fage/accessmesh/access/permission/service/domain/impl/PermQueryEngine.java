package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermBatchQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermBatchResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermEvalContext;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.TargetMode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper.BitMaskEntry;
import cn.ac.fage.accessmesh.access.permission.service.domain.BatchConditionEvaluator;
import cn.ac.fage.accessmesh.access.permission.service.domain.BatchPermMutexEvaluator;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一权限查询引擎 -- 所有权限校验的唯一入口（T-PERM-057 统一引擎：一个引擎、一套入参、一个结果模型）。
 *
 * <h3>管线阶段（query-engine-unification.md §4；角色互斥不归引擎——授权时校验另立项，
 * 快照/权限树的 filterRoleMutex 由调用方自理，2026-09-09 定案）</h3>
 * <ol>
 *   <li>入口封装：userId → roleIds（EFFECTIVE_ROLES 缓存）+ 条件上下文装配（四便捷入口自动取当前请求 clientIp）</li>
 *   <li>解析：类型值 / 操作 id / 位掩码（位覆盖并入掩码，常开）</li>
 *   <li>scopeAll 类型级查询（TYPE_LEVEL / INSTANCE）</li>
 *   <li>目标解析 + 判定面闭包（INSTANCE 且 inheritClosure：{目标}∪同类型祖先链入查询，作用于查询前）</li>
 *   <li>实例查询（INSTANCE 目标下推 / LIST 按角色全量，ROLE_PERM_SNAPSHOT 读缓存）</li>
 *   <li>条件评估（三态：评估 / 不评估 / 标记下发）</li>
 *   <li>条目级冲突过滤（开关，运行时面默认开）</li>
 *   <li>展示面展开（参数开时结果克隆，grantSource=INHERITED；不改变判定）</li>
 *   <li>操作投影展开（位覆盖投影轨，展示面消费）</li>
 *   <li>结果装配：双轨（原始授权行 + 覆盖投影）+ 辅助 map + 判定结论</li>
 * </ol>
 *
 * <h3>优化策略</h3>
 * <ul>
 *   <li>批量ID解析：避免N次单查询</li>
 *   <li>scopeAll优先匹配：匹配后跳过实例级查询</li>
 *   <li>判定面闭包走目标下推递归 CTE（止步同类型/软删截断/UNION 防环），不走全量图</li>
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
     * 执行统一权限查询（targetMode 三态判别，互不串义）
     *
     * @param q 权限查询参数
     * @return 权限查询结果
     */
    public PermResult query(PermQuery q) {
        // -- 0. 入口封装：userId → roleIds（EFFECTIVE_ROLES 缓存）+ evaluatedAt 一次钉住 --
        // 单次查询内多阶段（scopeAll/实例/主资源/范围）条件评估共用同一时钟，勿跨时间边界
        // 各取 now()（codex 四轮 P3；explain 已入口钉住，此处覆盖全部路径）
        if (q.evalContext() == null) {
            q.setEvalContext(new PermEvalContext(
                HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()),
                LocalDateTime.now(), Map.of()));
        } else if (q.evalContext().evaluatedAt() == null) {
            PermEvalContext pinned = new PermEvalContext(q.evalContext().clientIp(),
                LocalDateTime.now(), q.evalContext().attributes());
            q.setEvalContext(pinned);
        }
        Set<Long> roleIds = resolveRoleIds(q);
        if (roleIds.isEmpty()) {
            return PermResult.deny("NO_ROLE");
        }

        return switch (q.targetMode()) {
            case TYPE_LEVEL -> queryTypeLevel(q, roleIds);
            case INSTANCE -> queryInstanceMode(q, roleIds);
            case LIST -> queryList(q, roleIds);
        };
    }

    /**
     * TYPE_LEVEL：类型级门禁——无实例目标、只消费 scopeAll，不做实例查询
     * （实例级授权不得使命中，否则任何实例授权都会放行类型级门禁=越权）。
     */
    private PermResult queryTypeLevel(PermQuery q, Set<Long> roleIds) {
        ResolveContext ctx = prepareResolveContext(q);
        Set<Integer> resourceTypes = resolveResourceTypes(q, ctx);
        Set<Long> opIds = resolveOperationIds(q, ctx);
        Map<Integer, Long> bitMasks = resolveBitMasks(q.tenantId(), resourceTypes, opIds);

        List<RolePermEntry> scopeAllEntries = queryScopeAll(q.tenantId(), roleIds, bitMasks);
        // T-PERM-058：类型级门禁只认主授权——scopeAll 子权限行（depend_on 非空，写侧可造）
        // 不放行类型级门禁（无主资源上下文概念的面不消费上下文授权；子行 ALL 的唯一生效面
        // 为 LIST 父上下文内的 depend_on 过滤）。读侧排除，DB 直写脏数据同受防护。
        scopeAllEntries = scopeAllEntries.stream().filter(e -> e.dependOn() == null).toList();
        boolean scopeAllMatchedBeforeEval = !scopeAllEntries.isEmpty();
        scopeAllEntries = evaluateIfNeeded(q, scopeAllEntries);
        if (scopeAllEntries.isEmpty()) {
            // 拒绝原因区分（codex 外评 P2 修复，对齐基线语义）：有授权但条件/互斥评估清空 ≠ 无授权
            return PermResult.deny(scopeAllMatchedBeforeEval ? "CONDITION_NOT_MET_OR_CONFLICT" : "NO_PERMISSION");
        }

        PermResult.Builder builder = PermResult.builder(true, null)
            .scopeAllMatched(true)
            .scopeAllEntries(scopeAllEntries);
        loadAncillary(q, builder, scopeAllEntries, List.of(), roleIds);
        return builder.build();
    }

    /**
     * INSTANCE：实例判定——scopeAll 类型级命中（评估通过）即放行任意实例；
     * 未命中则目标下推实例查询（判定面继承开启时目标集扩为 {目标}∪同类型祖先链闭包）。
     */
    private PermResult queryInstanceMode(PermQuery q, Set<Long> roleIds) {
        ResolveContext ctx = prepareResolveContext(q);
        Set<Integer> resourceTypes = resolveResourceTypes(q, ctx);
        Set<Long> opIds = resolveOperationIds(q, ctx);
        Map<Integer, Long> bitMasks = resolveBitMasks(q.tenantId(), resourceTypes, opIds);

        // -- scopeAll 类型级优先：命中（评估通过）即放行，无需实例查询（精确实例模式跳过，
        //    explain scopeMode=INSTANCE 契约：按 resourceCode+codeType 精确匹配，不回退类型级）--
        // T-PERM-058：depend_on 子权限行按主资源上下文过滤（惰性父判定，两阶段共享一次）
        LazyParentCheck parentCheck = new LazyParentCheck(q, roleIds);
        boolean dependentOnlyExcluded = false;
        boolean scopeAllEvaluatedEmpty = false;
        if (!q.exactInstanceOnly()) {
            List<RolePermEntry> scopeAllEntries = queryScopeAll(q.tenantId(), roleIds, bitMasks);
            List<RolePermEntry> contextFilteredScopeAll = filterDependentEntries(q, scopeAllEntries, parentCheck);
            if (contextFilteredScopeAll.isEmpty() && !scopeAllEntries.isEmpty()) {
                dependentOnlyExcluded = true;
            }
            scopeAllEntries = contextFilteredScopeAll;
            if (!scopeAllEntries.isEmpty()) {
                scopeAllEntries = evaluateIfNeeded(q, scopeAllEntries);
                if (!scopeAllEntries.isEmpty()) {
                    PermResult.Builder builder = PermResult.builder(true, null)
                        .scopeAllMatched(true)
                        .scopeAllEntries(scopeAllEntries);
                    loadAncillary(q, builder, scopeAllEntries, List.of(), roleIds);
                    return builder.build();
                }
                // scopeAll 命中但评估清空：类型级路径已拒绝，实例级仍可命中（授权行各自评估）
                scopeAllEvaluatedEmpty = true;
            }
        }

        // -- 目标解析 + 判定面闭包（查询前扩大目标集）--
        Set<Long> targetEntityIds = resolveEntityIds(q);
        if (targetEntityIds.isEmpty()) {
            // 拒绝原因区分同下（codex 四轮 P2）：scopeAll 曾命中而被评估清空 + 目标不可解析
            // → 仍属「有授权但条件/冲突不满足」，勿报 NO_PERMISSION；scopeAll 子行被上下文排除同理
            if (scopeAllEvaluatedEmpty) {
                return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
            }
            if (dependentOnlyExcluded) {
                return PermResult.deny("DEPENDENT_NOT_IN_PARENT_CONTEXT");
            }
            return PermResult.deny("NO_PERMISSION");
        }
        Set<Long> queryEntityIds = targetEntityIds;
        if (q.inheritClosure()) {
            queryEntityIds = expandTargetsByClosure(q.tenantId(), targetEntityIds);
        }

        List<RolePermEntry> rawInstanceEntries = queryInstance(q.tenantId(), roleIds, queryEntityIds, bitMasks);
        List<RolePermEntry> instanceEntries = filterDependentEntries(q, rawInstanceEntries, parentCheck);
        if (instanceEntries.isEmpty() && !rawInstanceEntries.isEmpty()) {
            dependentOnlyExcluded = true;
        }
        boolean instanceMatchedBeforeEval = !instanceEntries.isEmpty();
        instanceEntries = evaluateIfNeeded(q, instanceEntries);
        if (instanceEntries.isEmpty()) {
            // 条件/冲突拒绝优先：过滤后仍有授权但被评估清空（codex 外评 P2 口径）
            boolean anyMatchedBeforeEval = instanceMatchedBeforeEval || scopeAllEvaluatedEmpty;
            if (anyMatchedBeforeEval) {
                return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
            }
            // 子权限行被主资源上下文排除（无上下文 fail-closed / 父未命中 / dependOn 不在父命中集）
            // 与「无任何授权」区分，便于排查（用户配了子权限但查询面无父上下文或父未命中）
            if (dependentOnlyExcluded) {
                return PermResult.deny("DEPENDENT_NOT_IN_PARENT_CONTEXT");
            }
            return PermResult.deny("NO_PERMISSION");
        }

        // -- 展示面展开（查询后克隆，不改变判定）--
        if (q.inheritParents() || q.inheritChildren()) {
            instanceEntries = expandByPresentMode(q.tenantId(), instanceEntries,
                q.inheritParents(), q.inheritChildren());
        }

        PermResult.Builder builder = PermResult.builder(true, null)
            .scopeAllMatched(false)
            .instanceEntries(instanceEntries);
        loadAncillary(q, builder, List.of(), instanceEntries, roleIds);
        return builder.build();
    }

    /**
     * LIST：全量清单——按角色全量拉取角色权限行（ROLE_PERM_SNAPSHOT 读缓存）；
     * 主资源上下文给出时执行 depend_on 子权限过滤（query-scopes 收编）。
     */
    private PermResult queryList(PermQuery q, Set<Long> roleIds) {
        List<RolePermEntry> allEntries = loadRolePermEntriesWithCache(q.tenantId(), roleIds, q.bypassPermSnapshot());
        if (allEntries.isEmpty()) {
            return PermResult.deny("NO_PERMISSION");
        }

        // -- 主资源上下文（depend_on 子权限过滤）：先对主资源做 INSTANCE 判定 --
        Set<String> parentMatchedOps = Set.of();
        Set<Long> parentPermissionIds = Set.of();
        boolean parentPresent = q.parentResourceTypeCode() != null && q.parentResourceCode() != null;
        if (parentPresent) {
            ParentCheckOutcome parent = checkParentResource(q, roleIds);
            parentMatchedOps = parent.matchedOperationCodes();
            parentPermissionIds = parent.matchedPermissionIds();
            if (parentPermissionIds.isEmpty()) {
                // 父资源无任何匹配权限：整体拒绝（全 DENIED 由调用方按四态组装）
                return PermResult.builder(false, "PARENT_NO_PERMISSION")
                    .scopeAllMatched(false)
                    .parentMatchedOperationCodes(parentMatchedOps)
                    .parentMatchedPermissionIds(parentPermissionIds)
                    .build();
            }
            // depend_on 过滤：子权限条目要求其 dependOn 指向的父权限在主资源命中集合内
            final Set<Long> allowedParents = parentPermissionIds;
            allEntries = allEntries.stream()
                .filter(entry -> entry.dependOn() == null || allowedParents.contains(entry.dependOn()))
                .toList();
            if (allEntries.isEmpty()) {
                return PermResult.builder(false, "NO_PERMISSION")
                    .scopeAllMatched(false)
                    .parentMatchedOperationCodes(parentMatchedOps)
                    .parentMatchedPermissionIds(parentPermissionIds)
                    .build();
            }
        }

        // 评估前原始条目（四态组装区分 DENIED/EMPTY 的事实源）
        List<RolePermEntry> rawEntries = List.copyOf(allEntries);
        allEntries = evaluateIfNeeded(q, allEntries);
        if (allEntries.isEmpty()) {
            // 评估清空 ≠ 整体拒绝：rawEntries 事实源仍在——四态组装区分 EMPTY/DENIED 需要
            // 操作定义在场（条件摘光的类型仍须装载其操作定义，grok 外评 P1 修复）
            PermResult.Builder denyBuilder = PermResult.builder(false, "CONDITION_NOT_MET_OR_CONFLICT")
                .scopeAllMatched(false)
                .rawEntries(rawEntries)
                .parentMatchedOperationCodes(parentMatchedOps)
                .parentMatchedPermissionIds(parentPermissionIds);
            loadAncillaryForView(q, denyBuilder, rawEntries, rawEntries, roleIds);
            return denyBuilder.build();
        }

        // -- 展示面展开（查询后克隆，不改变判定）--
        if (q.inheritParents() || q.inheritChildren()) {
            allEntries = expandByPresentMode(q.tenantId(), allEntries, q.inheritParents(), q.inheritChildren());
        }

        // LIST 不做 scopeAll/instance 语义断言，全部归入实例条目（forUserView 既有口径）
        PermResult.Builder builder = PermResult.builder(true, null)
            .scopeAllMatched(false)
            .instanceEntries(allEntries)
            .rawEntries(rawEntries)
            .parentMatchedOperationCodes(parentMatchedOps)
            .parentMatchedPermissionIds(parentPermissionIds);
        // 操作定义装载源用 rawEntries（评估后条目类型的超集）——条件摘光的类型仍须装载，
        // 否则四态组装层 covers 判定缺目标操作定义会把 EMPTY 误判 DENIED（grok 外评 P1）
        loadAncillaryForView(q, builder, allEntries, rawEntries, roleIds);
        return builder.build();
    }

    /** 主资源判定结果（LIST 模式 depend_on 过滤的父上下文）。 */
    private record ParentCheckOutcome(Set<String> matchedOperationCodes, Set<Long> matchedPermissionIds) {}

    // ===== 批量判定（T-PERM-061 A+ 形态：分组 + 请求级共享装载；设计定稿见 implementation §3.10）=====

    /** 批量分组键（六元；parentResource 请求级共享不进键）。 */
    private record BatchGroupKey(TargetMode mode, String typeCode, String opCode,
                                 String codeType, String domainCode, boolean inheritClosure) {}

    /**
     * 批量组运行态（共享装载与分段评估的中间物；outcomes 按 item 下标回填最终判定）。
     */
    private static final class BatchGroup {
        final BatchGroupKey key;
        final List<Integer> itemIndexes = new ArrayList<>();
        final Map<Integer, PermBatchResult.ItemOutcome> outcomes = new HashMap<>();
        Integer typeValue;
        Long opId;
        long coveringMask;
        boolean scopeAllPassed;
        boolean scopeAllMatchedBeforeEval;
        boolean scopeAllEvaluatedEmpty;
        boolean scopeAllDependentOnly;

        BatchGroup(BatchGroupKey key) {
            this.key = key;
        }
    }

    /**
     * 批量请求级共享上下文（per-request 实例经方法参数传递——@Component 单例禁止落引擎字段，
     * 实例字段会跨请求串数据）。全部 DB 新鲜读，不引入 ROLE_PERM_SNAPSHOT 陈旧语义
     * （L2_ONLY 10s 陈旧窗口不进运行时鉴权面，T-ACCESS-008 边界）。
     */
    private static final class BatchEvalContext {
        final Long tenantId;
        final Set<Long> roleIds;
        final Map<String, Object> evalMap;
        final BatchConditionEvaluator conditionEvaluator;
        final BatchPermMutexEvaluator mutexEvaluator;
        /** (组, ruleId) → 命中 item 下标（b2 定案 ledger；scopeAll 段与实例段分桶写入、Set 去重段间合并） */
        final Map<String, Map<Long, Set<Integer>>> mutexLedger = new LinkedHashMap<>();

        BatchEvalContext(Long tenantId, Set<Long> roleIds, Map<String, Object> evalMap,
                         BatchConditionEvaluator conditionEvaluator, BatchPermMutexEvaluator mutexEvaluator) {
            this.tenantId = tenantId;
            this.roleIds = roleIds;
            this.evalMap = evalMap;
            this.conditionEvaluator = conditionEvaluator;
            this.mutexEvaluator = mutexEvaluator;
        }
    }

    /**
     * 执行批量权限判定（batch-check 族，A+ 形态：分组 + 请求级共享装载）。
     * <p>
     * 装载共享收敛为常数、判定全部内存化；契约零变化（reason 词表 / matched 字段族与
     * 单条 forAuthCheck 逐 item 等价——等价差分回归锁钉死）。核心不变量（设计定稿 v4）：
     * </p>
     * <ul>
     *   <li>合并 SQL 切回投影谓词：组子集 = 同类型 + 组掩码；item 实例子集再按
     *       {@code entityId ∈ (inheritClosure ? 闭包∪自身 : {自身})} 收窄——false 档（含缺省）
     *       闭包集恒 {自身}，不得消费 CTE 映射（不分档 = 默认模式获得祖先继承 = 越权）；</li>
     *   <li>评估粒度：scopeAll 段组内一次（子集与 item 无关）、实例段逐 item（PERM_MUTEX
     *       集合语义——两端同场才冲突且两端全丢，合并评估必不等价）；</li>
     *   <li>评估顺序：每个投影子集固定 depend_on 过滤 → 条件评估 → PERM_MUTEX 计算
     *       （重排致条件摘掉互斥一端前两端同场全丢 = false deny + 虚假审计）；</li>
     *   <li>evaluatedAt 请求级单一时刻（a2 定案）：入口钉住唯一非空 PermEvalContext 挂全链。</li>
     * </ul>
     *
     * @param bq 批量查询参数（items 顺序即原始输入序，结果按下标对齐）
     * @return 批量结果（与 items 等长、下标对齐；拒绝项 matched 字段族恒空）
     */
    public PermBatchResult queryBatch(PermBatchQuery bq) {
        Long tenantId = bq.tenantId();
        // -- 0. 入口封装：唯一且非空 PermEvalContext 钉住请求级单一评估时刻（a2 定案）——
        //    全部 item 评估、父判定递归、条件评估共用同一实例；调用方已钉住时零操作，
        //    批量路径禁评估期 now() 回退（toEvalMap null 分支不触达）--
        PermEvalContext evalContext = bq.evalContext();
        if (evalContext == null || evalContext.evaluatedAt() == null) {
            evalContext = new PermEvalContext(
                evalContext != null ? evalContext.clientIp()
                    : HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()),
                LocalDateTime.now(),
                evalContext != null ? evalContext.attributes() : Map.of());
        }
        Map<String, Object> evalMap = evalContext.toEvalMap();

        // -- 1. 角色 ×1（空 = 整批 NO_ROLE 前置返回，与单条 query 入口同层）--
        Set<Long> roleIds;
        if (bq.roleIds() != null && !bq.roleIds().isEmpty()) {
            roleIds = bq.roleIds();
        } else if (bq.userId() == null) {
            roleIds = Set.of();
        } else {
            roleIds = subjectDomainService.resolveEffectiveRoles(tenantId, bq.userId());
        }
        List<PermBatchQuery.Item> items = bq.items();
        if (roleIds.isEmpty()) {
            List<PermBatchResult.ItemOutcome> outcomes = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                outcomes.add(PermBatchResult.ItemOutcome.deny("NO_ROLE"));
            }
            return new PermBatchResult(outcomes);
        }

        // -- 2. 分组 + 共享类型/操作解析（全类型一次、全 (type,op) 对逐类型批量）+ 逐组掩码 --
        List<BatchGroup> groups = buildBatchGroups(items);
        ResolveContext resolveCtx = new ResolveContext(tenantId, typeResolutionService);
        prepareBatchResolutions(groups, resolveCtx);
        computeGroupMasks(tenantId, groups);

        BatchEvalContext ctx = new BatchEvalContext(tenantId, roleIds, evalMap,
            conditionDomainService.openBatchEvaluator(tenantId),
            conflictDomainService.openBatchMutexEvaluator(tenantId));

        // 共享父判定载体（惰性；共享只经既有注入面 setRoleIds+evalContext——父类型/父操作
        // 解析不并入共享 ResolveContext：父判定每请求至多一次，其内部解析天然一次）
        PermQuery parentCarrier = PermQuery.forAuthCheck(tenantId, bq.userId(), null, null, null);
        parentCarrier.setRoleIds(roleIds);
        parentCarrier.setEvalContext(evalContext);
        if (bq.parentResourceTypeCode() != null && bq.parentResourceCode() != null) {
            parentCarrier.setParentResource(bq.parentResourceTypeCode(), bq.parentResourceCode(),
                bq.parentCodeType(), bq.parentOperationCodes());
        }
        LazyParentCheck parentCheck = new LazyParentCheck(parentCarrier, roleIds);

        // -- 3. scopeAll 行 ×1（全组 BitMaskEntry 合并一次 SQL）+ 段内评估（组内一次）--
        List<RolePermEntry> scopeAllRows = queryScopeAllForGroups(tenantId, roleIds, groups);
        // 条件增量快照：scopeAll 段行集到手后一次预载（每阶段至多一批次）
        ctx.conditionEvaluator.preload(tenantId, collectConditionIds(scopeAllRows));
        List<BatchGroup> unresolvedGroups = new ArrayList<>();
        for (BatchGroup group : groups) {
            evalBatchScopeAllSegment(ctx, group, scopeAllRows, parentCarrier, parentCheck);
            if (!group.scopeAllPassed) {
                unresolvedGroups.add(group);
            }
        }

        // -- 4. 实例段（仅 scopeAll 未放行组；分段化装载——scopeAll 命中即短路是既有优化）--
        evalBatchInstanceSegment(ctx, items, unresolvedGroups, parentCarrier, parentCheck);

        // -- 5. 互斥通知 ledger flush（每 (组, ruleId) 一条审计行；单出口——scopeAll 短路组入账后才返回）--
        flushMutexLedger(ctx);

        // -- 6. 结果按原始输入序组装 --
        List<PermBatchResult.ItemOutcome> outcomes = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            outcomes.add(null);
        }
        for (BatchGroup group : groups) {
            for (int idx : group.itemIndexes) {
                PermBatchResult.ItemOutcome outcome = group.outcomes.get(idx);
                outcomes.set(idx, outcome != null ? outcome
                    : PermBatchResult.ItemOutcome.deny("NO_PERMISSION"));
            }
        }
        return new PermBatchResult(outcomes);
    }

    /** 按 (targetMode, type, op, codeType, domainCode, inheritClosure) 六元分组，保序记录 item 下标。 */
    private List<BatchGroup> buildBatchGroups(List<PermBatchQuery.Item> items) {
        Map<BatchGroupKey, BatchGroup> byKey = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            PermBatchQuery.Item item = items.get(i);
            TargetMode mode = item.resourceCode() == null ? TargetMode.TYPE_LEVEL : TargetMode.INSTANCE;
            BatchGroupKey key = new BatchGroupKey(mode, item.resourceTypeCode(), item.operationCode(),
                item.codeType(), item.domainCode(), item.inheritClosure());
            byKey.computeIfAbsent(key, BatchGroup::new).itemIndexes.add(i);
        }
        return new ArrayList<>(byKey.values());
    }

    /** 共享类型/操作解析：全类型一次 + 全 (type,op) 对逐类型批量，组上回填解析结果。 */
    private void prepareBatchResolutions(List<BatchGroup> groups, ResolveContext resolveCtx) {
        Set<String> typeCodes = new LinkedHashSet<>();
        Map<String, Set<String>> opsByType = new LinkedHashMap<>();
        for (BatchGroup group : groups) {
            typeCodes.add(group.key.typeCode());
            opsByType.computeIfAbsent(group.key.typeCode(), _unused -> new LinkedHashSet<>())
                .add(group.key.opCode());
        }
        resolveCtx.prepareResourceTypes(typeCodes);
        for (Map.Entry<String, Set<String>> entry : opsByType.entrySet()) {
            resolveCtx.prepareOperations(entry.getKey(), entry.getValue());
        }
        // 批量解析结果即权威（缓存直取，无单条回退）——幽灵类型/操作码不再逐组单条探测
        for (BatchGroup group : groups) {
            group.typeValue = resolveCtx.getCachedTypeValue(group.key.typeCode());
            group.opId = resolveCtx.getCachedOperationId(group.key.typeCode(), group.key.opCode());
        }
    }

    /** 逐组覆盖掩码（全组操作并集一次装载 + 类型操作索引共享；位覆盖常开）。 */
    private void computeGroupMasks(Long tenantId, List<BatchGroup> groups) {
        Set<Long> allOpIds = new LinkedHashSet<>();
        Set<Integer> typeValues = new LinkedHashSet<>();
        for (BatchGroup group : groups) {
            if (group.opId != null) {
                allOpIds.add(group.opId);
            }
            if (group.typeValue != null) {
                typeValues.add(group.typeValue);
            }
        }
        if (allOpIds.isEmpty() || typeValues.isEmpty()) {
            return;
        }
        Map<Long, OperationPermission> targetOps = batchLoadOperations(tenantId, allOpIds);
        Map<Integer, Map<Long, OperationPermission>> typeOpIndex = loadOperationIndexByType(tenantId, typeValues);
        for (BatchGroup group : groups) {
            if (group.typeValue == null || group.opId == null) {
                continue;
            }
            OperationPermission targetOp = targetOps.get(group.opId);
            if (targetOp == null || !Objects.equals(targetOp.getResourceType(), group.typeValue)) {
                continue;
            }
            Map<Long, OperationPermission> ops = typeOpIndex.get(group.typeValue);
            if (ops == null || ops.isEmpty()) {
                continue;
            }
            group.coveringMask = OperationPermissionUtils.computeCoveringBitMask(ops.values(), targetOp.getBinaryBit());
        }
    }

    /** scopeAll 行 ×1：全组 BitMaskEntry 合并（同类型 OR 形成超集谓词，切回时按组掩码收窄）。 */
    private List<RolePermEntry> queryScopeAllForGroups(Long tenantId, Set<Long> roleIds, List<BatchGroup> groups) {
        Map<Integer, Long> merged = new LinkedHashMap<>();
        for (BatchGroup group : groups) {
            if (group.typeValue == null || group.coveringMask == 0L) {
                continue;
            }
            merged.merge(group.typeValue, group.coveringMask, (a, b) -> a | b);
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        List<BitMaskEntry> entries = merged.entrySet().stream()
            .map(e -> new BitMaskEntry(e.getKey(), e.getValue()))
            .toList();
        return rolePermMapper.selectScopeAllPermsByBitsBatch(tenantId, roleIds, entries)
            .stream()
            .map(entryMapper::toEntry)
            .toList();
    }

    /** 组子集切分：resourceType == 组类型 AND (grantedBits & 组掩码) != 0（投影谓词——缺类型谓词即跨组泄漏）。 */
    private List<RolePermEntry> filterByGroupMask(List<RolePermEntry> rows, BatchGroup group) {
        if (group.typeValue == null || group.coveringMask == 0L) {
            return List.of();
        }
        return rows.stream()
            .filter(e -> Objects.equals(e.resourceType(), group.typeValue)
                && e.grantedBits() != null && (e.grantedBits() & group.coveringMask) != 0)
            .toList();
    }

    /**
     * scopeAll 段评估（组内一次——子集与 item 无关）。
     * <p>
     * TYPE_LEVEL 组：无条件丢子行（queryTypeLevel 现状，二值 reason，永不 DEPENDENT）；
     * INSTANCE 组：filterDependentEntries（无父剥子行/有父按父命中集留，惰性共享父判定）；
     * 评估通过即组放行（短路优先——全部 item allowed，无论 code 可解析与否）。
     * </p>
     */
    private void evalBatchScopeAllSegment(BatchEvalContext ctx, BatchGroup group,
                                          List<RolePermEntry> scopeAllRows,
                                          PermQuery carrier, LazyParentCheck parentCheck) {
        List<RolePermEntry> subset = filterByGroupMask(scopeAllRows, group);
        if (group.key.mode() == TargetMode.TYPE_LEVEL) {
            // 类型级门禁只认主授权——depend_on 子权限行读侧排除（T-PERM-058 现状）
            subset = subset.stream().filter(e -> e.dependOn() == null).toList();
        } else {
            List<RolePermEntry> contextFiltered = filterDependentEntries(carrier, subset, parentCheck);
            if (contextFiltered.isEmpty() && !subset.isEmpty()) {
                group.scopeAllDependentOnly = true;
            }
            subset = contextFiltered;
        }
        group.scopeAllMatchedBeforeEval = !subset.isEmpty();
        if (subset.isEmpty()) {
            return;
        }
        // 评估顺序不变量：depend_on 过滤（上方）→ 条件评估 → PERM_MUTEX 计算（不得重排）
        List<RolePermEntry> evaluated = ctx.conditionEvaluator.evaluate(ctx.tenantId, subset, ctx.evalMap);
        BatchPermMutexEvaluator.PermMutexComputation mutex = ctx.mutexEvaluator.compute(evaluated);
        // scopeAll 段 ledger：子集与 item 无关——命中规则记账组内全部 item
        recordMutexHit(ctx, group, mutex.triggeredRuleIds(), group.itemIndexes);
        evaluated = mutex.filtered();
        if (evaluated.isEmpty()) {
            // scopeAll 命中但评估清空：INSTANCE 组实例级仍可命中（授权行各自评估）
            group.scopeAllEvaluatedEmpty = true;
            return;
        }
        group.scopeAllPassed = true;
        PermBatchResult.ItemOutcome allowed = allowOutcomeOf(evaluated);
        for (int idx : group.itemIndexes) {
            group.outcomes.put(idx, allowed);
        }
    }

    /**
     * 实例段（仅 scopeAll 未放行组；分段化装载）。
     * <p>
     * entity 预解析 ×1（按返回 Map 键取）；闭包 CTE 仅对 true 档目标发一次（false 档含缺省
     * 闭包集恒 {自身}）；实例 SQL 一次（未放行组合并）；逐 item 评估（PERM_MUTEX 集合语义）。
     * 空目标集守卫：可解析 entityId 并集为空（纯 TYPE_LEVEL 批 / 全幽灵 code）不调闭包 CTE
     * （空 foreach IN() = PG 语法错误 500）与实例 SQL（<if> 空集丢实体过滤 = 无界装载）。
     * </p>
     */
    private void evalBatchInstanceSegment(BatchEvalContext ctx, List<PermBatchQuery.Item> items,
                                          List<BatchGroup> unresolvedGroups,
                                          PermQuery carrier, LazyParentCheck parentCheck) {
        List<BatchGroup> instanceGroups = unresolvedGroups.stream()
            .filter(group -> group.key.mode() == TargetMode.INSTANCE)
            .toList();
        // TYPE_LEVEL 组在 scopeAll 段已全部出结果（二值 reason——无实例查询）
        for (BatchGroup group : unresolvedGroups) {
            if (group.key.mode() == TargetMode.TYPE_LEVEL) {
                String reason = group.scopeAllMatchedBeforeEval ? "CONDITION_NOT_MET_OR_CONFLICT" : "NO_PERMISSION";
                for (int idx : group.itemIndexes) {
                    group.outcomes.put(idx, PermBatchResult.ItemOutcome.deny(reason));
                }
            }
        }
        if (instanceGroups.isEmpty()) {
            return;
        }

        // -- entity 预解析 ×1：实例段全部 item 的 code 合并一次（distinct 解析键）--
        List<ResourceResolveRequest> requests = new ArrayList<>();
        Set<ResourceResolveKey> seenKeys = new HashSet<>();
        for (BatchGroup group : instanceGroups) {
            for (int idx : group.itemIndexes) {
                PermBatchQuery.Item item = items.get(idx);
                if (item.resourceCode() == null) {
                    continue;
                }
                ResourceResolveKey key = new ResourceResolveKey(item.resourceTypeCode(), item.resourceCode(),
                    item.codeType(), item.domainCode());
                if (seenKeys.add(key)) {
                    requests.add(new ResourceResolveRequest(item.resourceTypeCode(), item.resourceCode(),
                        item.codeType(), item.domainCode()));
                }
            }
        }
        Map<ResourceResolveKey, Long> entityIdByKey = requests.isEmpty() ? Map.of()
            : typeResolutionService.batchResolveResourceIds(ctx.tenantId, requests);
        Map<Integer, Long> entityIdByIndex = new HashMap<>();
        for (BatchGroup group : instanceGroups) {
            for (int idx : group.itemIndexes) {
                PermBatchQuery.Item item = items.get(idx);
                if (item.resourceCode() != null) {
                    entityIdByIndex.put(idx, entityIdByKey.get(new ResourceResolveKey(
                        item.resourceTypeCode(), item.resourceCode(), item.codeType(), item.domainCode())));
                }
            }
        }
        Set<Long> resolvedIds = new LinkedHashSet<>();
        for (Long entityId : entityIdByIndex.values()) {
            if (entityId != null) {
                resolvedIds.add(entityId);
            }
        }
        if (resolvedIds.isEmpty()) {
            // 空目标集守卫：全部幽灵/不可解析——各 item 走目标空 reason 树，不下推实例 SQL 与闭包 CTE
            for (BatchGroup group : instanceGroups) {
                for (int idx : group.itemIndexes) {
                    group.outcomes.put(idx, targetEmptyOutcome(group));
                }
            }
            return;
        }

        // -- 闭包映射 ×1（按档分用）：仅 true 档目标发 CTE；false 档闭包集恒 {自身} --
        Set<Long> closureTargets = new LinkedHashSet<>();
        for (Map.Entry<Integer, Long> entry : entityIdByIndex.entrySet()) {
            if (entry.getValue() != null && items.get(entry.getKey()).inheritClosure()) {
                closureTargets.add(entry.getValue());
            }
        }
        Map<Long, Set<Long>> closureByTarget = new LinkedHashMap<>();
        if (!closureTargets.isEmpty()) {
            for (ResourceEntityMapper.AncestorClosureResult pair :
                resourceEntityMapper.selectSelfAndAncestorClosureBatch(ctx.tenantId, closureTargets)) {
                closureByTarget.computeIfAbsent(pair.getTargetId(), _unused -> new LinkedHashSet<>())
                    .add(pair.getClosureId());
            }
        }
        Set<Long> queryEntityIds = new LinkedHashSet<>();
        for (Map.Entry<Integer, Long> entry : entityIdByIndex.entrySet()) {
            Long entityId = entry.getValue();
            if (entityId == null) {
                continue;
            }
            if (items.get(entry.getKey()).inheritClosure()) {
                queryEntityIds.addAll(closureByTarget.getOrDefault(entityId, Set.of(entityId)));
            } else {
                queryEntityIds.add(entityId);
            }
        }

        // -- 实例行 ×1：未放行组 BitMaskEntry 合并（同类型 OR 超集，切回按 item 闭包集收窄）--
        Map<Integer, Long> mergedMask = new LinkedHashMap<>();
        for (BatchGroup group : instanceGroups) {
            if (group.typeValue == null || group.coveringMask == 0L) {
                continue;
            }
            mergedMask.merge(group.typeValue, group.coveringMask, (a, b) -> a | b);
        }
        List<RolePermEntry> instanceRows = queryInstance(ctx.tenantId, ctx.roleIds, queryEntityIds, mergedMask);
        // 条件增量快照：实例段行集到手后一次预载（每阶段至多一批次）
        ctx.conditionEvaluator.preload(ctx.tenantId, collectConditionIds(instanceRows));

        for (BatchGroup group : instanceGroups) {
            for (int idx : group.itemIndexes) {
                evalBatchItem(ctx, group, idx, items.get(idx), entityIdByIndex.get(idx),
                    instanceRows, closureByTarget, carrier, parentCheck);
            }
        }
    }

    /** 单 item 实例段评估（投影谓词三分量 + 顺序不变量 + reason 三支）。 */
    private void evalBatchItem(BatchEvalContext ctx, BatchGroup group, int idx, PermBatchQuery.Item item,
                               Long targetEntityId, List<RolePermEntry> instanceRows,
                               Map<Long, Set<Long>> closureByTarget,
                               PermQuery carrier, LazyParentCheck parentCheck) {
        if (targetEntityId == null) {
            // 目标空（code 预解析失败）：scopeAll 段三支（对齐单条目标空分支）
            group.outcomes.put(idx, targetEmptyOutcome(group));
            return;
        }
        // item 实例子集：同类型+组掩码+entityId ∈ (inheritClosure ? cteClosure[target]∪{target} : {target})
        Set<Long> closure = item.inheritClosure()
            ? closureByTarget.getOrDefault(targetEntityId, Set.of(targetEntityId))
            : Set.of(targetEntityId);
        List<RolePermEntry> subset = instanceRows.stream()
            .filter(e -> Objects.equals(e.resourceType(), group.typeValue)
                && e.grantedBits() != null && (e.grantedBits() & group.coveringMask) != 0
                && e.resourceEntityId() != null && closure.contains(e.resourceEntityId()))
            .toList();
        // 评估顺序不变量：depend_on 过滤 → 条件评估 → PERM_MUTEX 计算（不得重排）
        List<RolePermEntry> filtered = filterDependentEntries(carrier, subset, parentCheck);
        boolean itemDependentOnly = filtered.isEmpty() && !subset.isEmpty();
        boolean instanceMatchedBeforeEval = !filtered.isEmpty();
        List<RolePermEntry> evaluated = ctx.conditionEvaluator.evaluate(ctx.tenantId, filtered, ctx.evalMap);
        BatchPermMutexEvaluator.PermMutexComputation mutex = ctx.mutexEvaluator.compute(evaluated);
        recordMutexHit(ctx, group, mutex.triggeredRuleIds(), List.of(idx));
        evaluated = mutex.filtered();
        if (!evaluated.isEmpty()) {
            group.outcomes.put(idx, allowOutcomeOf(evaluated));
            return;
        }
        // 拒绝原因三支（对齐单条评估清空分支：条件/冲突拒绝优先，scopeAll 段标志 OR 合并）
        boolean anyMatchedBeforeEval = instanceMatchedBeforeEval || group.scopeAllEvaluatedEmpty;
        String reason = anyMatchedBeforeEval ? "CONDITION_NOT_MET_OR_CONFLICT"
            : (itemDependentOnly || group.scopeAllDependentOnly) ? "DEPENDENT_NOT_IN_PARENT_CONTEXT"
            : "NO_PERMISSION";
        group.outcomes.put(idx, PermBatchResult.ItemOutcome.deny(reason));
    }

    /** 目标空分支 reason（scopeAll 段三支；TYPE_LEVEL 组不触达）。 */
    private static PermBatchResult.ItemOutcome targetEmptyOutcome(BatchGroup group) {
        String reason = group.scopeAllEvaluatedEmpty ? "CONDITION_NOT_MET_OR_CONFLICT"
            : group.scopeAllDependentOnly ? "DEPENDENT_NOT_IN_PARENT_CONTEXT"
            : "NO_PERMISSION";
        return PermBatchResult.ItemOutcome.deny(reason);
    }

    /** 互斥命中记账（(组, ruleId) → 命中下标集合；scopeAll 段=组内全部 item、实例段=该 item）。 */
    private void recordMutexHit(BatchEvalContext ctx, BatchGroup group, Set<Long> triggeredRuleIds,
                                List<Integer> hitIndexes) {
        if (triggeredRuleIds == null || triggeredRuleIds.isEmpty() || hitIndexes.isEmpty()) {
            return;
        }
        Map<Long, Set<Integer>> bucket = ctx.mutexLedger
            .computeIfAbsent(groupKeyString(group.key), _unused -> new LinkedHashMap<>());
        for (Long ruleId : triggeredRuleIds) {
            bucket.computeIfAbsent(ruleId, _unused -> new LinkedHashSet<>()).addAll(hitIndexes);
        }
    }

    /** ledger flush：每 (组, ruleId) 一条审计行（hitItemCount = item 去重、段间合并后计数）。 */
    private void flushMutexLedger(BatchEvalContext ctx) {
        if (ctx.mutexLedger.isEmpty()) {
            return;
        }
        List<BatchPermMutexEvaluator.MutexHit> hits = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Set<Integer>>> groupEntry : ctx.mutexLedger.entrySet()) {
            for (Map.Entry<Long, Set<Integer>> ruleEntry : groupEntry.getValue().entrySet()) {
                hits.add(new BatchPermMutexEvaluator.MutexHit(
                    groupEntry.getKey(), ruleEntry.getKey(), ruleEntry.getValue().size()));
            }
        }
        ctx.mutexEvaluator.notifyHits(ctx.tenantId, hits);
    }

    /** 允许项工厂（matched 字段族由评估后条目派生；组内共享条目集）。 */
    private static PermBatchResult.ItemOutcome allowOutcomeOf(List<RolePermEntry> entries) {
        Set<Long> matchedRoleIds = new LinkedHashSet<>();
        Set<Long> matchedPermissionIds = new LinkedHashSet<>();
        for (RolePermEntry entry : entries) {
            if (entry.roleId() != null) {
                matchedRoleIds.add(entry.roleId());
            }
            if (entry.permissionId() != null) {
                matchedPermissionIds.add(entry.permissionId());
            }
        }
        return PermBatchResult.ItemOutcome.allow(matchedRoleIds, matchedPermissionIds);
    }

    /** 行集引用的条件 ID 并集（增量快照 preload 入参）。 */
    private static Set<Long> collectConditionIds(List<RolePermEntry> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (RolePermEntry entry : rows) {
            if (entry.conditionId() != null && entry.hasCondition()) {
                ids.add(entry.conditionId());
            }
        }
        return ids;
    }

    /** 组键可读串（互斥 ledger / 审计 detail 消费）。 */
    private static String groupKeyString(BatchGroupKey key) {
        return key.mode() + ":" + key.typeCode() + ":" + key.opCode()
            + ":" + (key.codeType() == null ? "-" : key.codeType())
            + ":" + (key.domainCode() == null ? "-" : key.domainCode())
            + ":" + (key.inheritClosure() ? "CLOSURE" : "FLAT");
    }

    /**
     * depend_on 父判定惰性缓存（单次 INSTANCE 查询内 scopeAll 前置与实例查询两阶段
     * 共享一次父 INSTANCE 判定；主行命中的常规路径零额外查询）。
     */
    private final class LazyParentCheck {
        private final PermQuery q;
        private final Set<Long> roleIds;
        private Set<Long> matchedPermissionIds;
        private boolean resolved;

        LazyParentCheck(PermQuery q, Set<Long> roleIds) {
            this.q = q;
            this.roleIds = roleIds;
        }

        Set<Long> matchedPermissionIds() {
            if (!resolved) {
                ParentCheckOutcome parent = checkParentResource(q, roleIds);
                matchedPermissionIds = parent.matchedPermissionIds() == null
                    ? Set.of() : parent.matchedPermissionIds();
                resolved = true;
            }
            return matchedPermissionIds;
        }
    }

    /**
     * depend_on 子权限行的主资源上下文过滤（T-PERM-058 单点面闭合）。
     * <p>
     * 子权限行（depend_on 非空）的授权语义是「只在父权限命中的主资源上下文内生效」
     * （api-contract §6.7 DEPENDENT 公式；此前仅 LIST 带 parentResource 路径实现，
     * 单点 INSTANCE 路径直接命中子行=绕过父绑定）。本过滤将同一语义接入 INSTANCE 路径：
     * <ul>
     *   <li>结果不含子行时原样返回（零开销，主行命中的常规路径不受影响）；</li>
     *   <li>无主资源上下文（parentResourceTypeCode/Code 未给）→ 子权限行一律不计入
     *       （fail-closed，与 TYPE_LEVEL 不消费实例授权的三态原则同构）；</li>
     *   <li>给出上下文 → 惰性执行父 INSTANCE 判定（含条件/互斥评估），子行要求其
     *       dependOn ∈ 父命中权限 id 集（与 LIST 路径 depend_on 过滤同构；
     *       父判定经 forAuthCheck 递归本引擎，自身无父上下文=只认父的主授权，单层语义）。</li>
     * </ul>
     * </p>
     */
    private List<RolePermEntry> filterDependentEntries(PermQuery q, List<RolePermEntry> entries,
                                                       LazyParentCheck parentCheck) {
        List<RolePermEntry> dependent = entries.stream()
            .filter(e -> e.dependOn() != null).toList();
        if (dependent.isEmpty()) {
            return entries;
        }
        List<RolePermEntry> main = entries.stream()
            .filter(e -> e.dependOn() == null).toList();
        if (q.parentResourceTypeCode() == null || q.parentResourceCode() == null) {
            return main;
        }
        Set<Long> allowedParents = parentCheck.matchedPermissionIds();
        if (allowedParents.isEmpty()) {
            return main;
        }
        List<RolePermEntry> merged = new ArrayList<>(main);
        dependent.stream()
            .filter(e -> allowedParents.contains(e.dependOn()))
            .forEach(merged::add);
        return merged;
    }

    /**
     * 对主资源做 INSTANCE 判定（一次查询覆盖 parentOperationCodes 全集，内存逐操作 covers 判定）。
     */
    private ParentCheckOutcome checkParentResource(PermQuery q, Set<Long> roleIds) {
        Set<String> parentOps = q.parentOperationCodes() == null ? Set.of() : q.parentOperationCodes();
        PermQuery parentQuery = PermQuery.forAuthCheck(q.tenantId(), q.userId(),
            q.parentResourceTypeCode(), q.parentResourceCode(), null);
        parentQuery.setRoleIds(roleIds);
        parentQuery.setCodeType(q.parentCodeType());
        parentQuery.setOperationCodes(parentOps.isEmpty() ? Set.of() : parentOps);
        parentQuery.setEvaluateMatchesBit(true);
        // 逐操作命中解析依赖 operationMap 装配（forAuthCheck 最小输出默认不装配）；
        // 条件上下文透传调用方（父资源挂条件授权时按真实 clientIp 评估，勿退化为空上下文）
        parentQuery.setIncludeOperations(true);
        parentQuery.setEvalContext(q.evalContext());
        PermResult parentResult = query(parentQuery);
        if (!parentResult.allowed()) {
            return new ParentCheckOutcome(Set.of(), Set.of());
        }
        Set<Long> parentPermissionIds = parentResult.matchedPermissionIds();
        // 逐操作命中判定：掩码合并查询后按条目 grantedBits 与各目标操作 covers 展开
        Set<String> matchedOps = resolveMatchedOperationCodes(q, parentResult, parentOps);
        return new ParentCheckOutcome(matchedOps, parentPermissionIds);
    }

    /**
     * 从 INSTANCE 判定结果条目解析逐操作命中集合（哪些 parentOperationCode 实际被授权覆盖）。
     */
    private Set<String> resolveMatchedOperationCodes(PermQuery q, PermResult parentResult, Set<String> parentOps) {
        if (parentOps.isEmpty() || parentResult.operationMap() == null || parentResult.operationMap().isEmpty()) {
            return Set.of();
        }
        Map<String, OperationPermission> targetOpByCode = new LinkedHashMap<>();
        for (Map.Entry<String, Long> opIdEntry : typeResolutionService
            .batchResolveOperationIds(q.tenantId(), q.parentResourceTypeCode(), parentOps).entrySet()) {
            OperationPermission target = parentResult.operationMap().get(opIdEntry.getValue());
            if (target != null) {
                targetOpByCode.put(opIdEntry.getKey(), target);
            }
        }
        if (targetOpByCode.isEmpty()) {
            return Set.of();
        }
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils
            .indexByResourceTypeAndBinaryBit(parentResult.operationMap().values());
        Set<String> matched = new LinkedHashSet<>();
        for (RolePermEntry entry : parentResult.allEntries()) {
            OperationPermission granted = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                grantedOpIndex, entry.resourceType(), entry.grantedBits());
            if (granted == null) {
                continue;
            }
            for (Map.Entry<String, OperationPermission> target : targetOpByCode.entrySet()) {
                if (OperationPermissionUtils.covers(granted, target.getValue())) {
                    matched.add(target.getKey());
                }
            }
        }
        return matched;
    }

    // ===== 引擎便捷 API（T-ACCESS-016 定稿终态，T-PERM-042 落地）=====
    // code 与 entityId 两轨不得混用：
    //   - hasPermissionByCode / getDeniedResourceCodes：业务编码语义，对外（USER/ROLE 等业务对象门禁与跨服务 SDK）。
    //     resource_entity(USER).code = subjectId、resource_entity(ROLE).code = roleId（architecture §12.3）。
    //   - hasPermissionByEntityId / getDeniedEntityIds：resource_entity.id 语义，仅引擎内部或已完成解析的调用方
    //     （资源树、API 映射、资源依赖、权限树等直接管理资源实体的后台链路）。
    // 引擎纯查询不抛 SecurityException；异常由调用方显式抛出（admin 域经 AdminPermissionValidator 门面、
    // permission 域 AppService if-throw）。
    // T-PERM-057 拉平口径（管理面写门禁）：条件评估开（入口自动装配当前请求 clientIp——explain 先例；
    // 无请求上下文则 IP 类条件按 ConditionEvalUtils 既有 fail-closed 拒绝）、条目互斥开、判定面继承开。

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
        autoFillEvalContext(q);
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
        autoFillEvalContext(q);
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
     * 内部解析语义一致）。T-PERM-057：判定面继承（管理面写门禁默认开）——目标集扩为
     * {目标}∪同类型祖先链，条目挂祖先实体经闭包回映射判目标允许（评估口径拉平：条件+条目互斥）。
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

        Map<String, Object> evalMap = autoEvalMap();
        // 3. 类型级 scopeAll 优先（含条件与冲突评估）：命中则全部允许，不做 code 解析
        Map<Integer, Long> bitMasks = resolveBitMasks(tenantId, Set.of(resourceTypeValue), Set.of(operationId));
        if (passesScopeAll(tenantId, roleIds, bitMasks, evalMap)) {
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

        // 5. 实例级批量查询（含条件与冲突评估）+ 闭包回映射；未解析的 code 一并拒绝（fail-closed）
        Set<Long> deniedEntityIds = computeInstanceDenied(
            tenantId, roleIds, new LinkedHashSet<>(entityIdByCode.values()), bitMasks, evalMap, true);
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
     * 一次批量实例级查询（含条件与冲突评估）→ 内存计算拒绝集合（判定面继承开启时经闭包回映射，
     * 条目挂同类型祖先实体的目标判允许）。
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

        Map<String, Object> evalMap = autoEvalMap();
        // 3. 类型级 scopeAll 优先（含条件与冲突评估）：命中则全部允许
        Map<Integer, Long> bitMasks = resolveBitMasks(tenantId, Set.of(resourceTypeValue), Set.of(operationId));
        if (passesScopeAll(tenantId, roleIds, bitMasks, evalMap)) {
            return Set.of();
        }

        // 4. 实例级批量查询（含条件与冲突评估）+ 闭包回映射计算拒绝集合
        return computeInstanceDenied(tenantId, roleIds, resourceEntityIds, bitMasks, evalMap, true);
    }

    /**
     * 自动装配条件评估上下文（四便捷入口——管理面写门禁拉平为评估后，
     * 从当前请求装配 clientIp；无请求上下文（内部调用）则 IP 类条件按 fail-closed 拒绝，
     * 时间/日期类条件用评估方时钟。explain 链路 T-PERM-033 先例）。
     */
    private static void autoFillEvalContext(PermQuery q) {
        if (q.evalContext() != null) {
            return;
        }
        q.setEvalContext(new PermEvalContext(
            HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()), null, Map.of()));
    }

    private static Map<String, Object> autoEvalMap() {
        return new PermEvalContext(
            HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()), null, Map.of()).toEvalMap();
    }

    /**
     * 类型级 scopeAll 是否放行（含条件与冲突评估）。
     * <p>
     * scopeAll 命中且评估非空即放行该资源类型的任意实例；条件或冲突评估清空条目则视为未命中。
     * </p>
     */
    private boolean passesScopeAll(Long tenantId, Set<Long> roleIds, Map<Integer, Long> bitMasks,
                                    Map<String, Object> evalMap) {
        List<RolePermEntry> scopeAllEntries = queryScopeAll(tenantId, roleIds, bitMasks);
        // T-PERM-058：批量便捷入口无主资源上下文——子权限行不计入（fail-closed，与单点面同口径）
        scopeAllEntries = scopeAllEntries.stream().filter(e -> e.dependOn() == null).toList();
        if (scopeAllEntries.isEmpty()) {
            return false;
        }
        List<RolePermEntry> evaluated = conditionDomainService.evaluate(tenantId, scopeAllEntries, evalMap);
        if (evaluated.isEmpty()) {
            return false;
        }
        evaluated = conflictDomainService.filterPermMutex(tenantId, evaluated);
        return !evaluated.isEmpty();
    }

    /**
     * 实例级批量查询（含条件与冲突评估）并计算拒绝集合（判定面继承闭包回映射）。
     * <p>
     * 调用方须已完成角色/类型/操作解析与 scopeAll 检查（未命中路径的共享实例步骤）。
     * inheritClosure 开启时（管理面写门禁/读过滤面默认）：目标集扩为 {目标}∪同类型祖先链，
     * 实例条目可能挂在祖先实体上——拒绝判定按「目标的闭包集与条目实体集交集为空」回映射
     * （实现成败点，query-engine-unification.md §10.2：条目挂祖先、请求目标不在条目实体集
     * 不得误判 DENIED）。
     * </p>
     */
    private Set<Long> computeInstanceDenied(Long tenantId, Set<Long> roleIds,
                                             Set<Long> resourceEntityIds, Map<Integer, Long> bitMasks,
                                             Map<String, Object> evalMap, boolean inheritClosure) {
        // 目标闭包：target → {自身}∪同类型祖先
        Map<Long, Set<Long>> closureByTarget = new LinkedHashMap<>();
        Set<Long> queryEntityIds = new LinkedHashSet<>(resourceEntityIds);
        if (inheritClosure) {
            for (ResourceEntityMapper.AncestorClosureResult pair :
                resourceEntityMapper.selectSelfAndAncestorClosureBatch(tenantId, resourceEntityIds)) {
                closureByTarget.computeIfAbsent(pair.getTargetId(), _unused -> new LinkedHashSet<>())
                    .add(pair.getClosureId());
                queryEntityIds.add(pair.getClosureId());
            }
        } else {
            for (Long entityId : resourceEntityIds) {
                closureByTarget.put(entityId, Set.of(entityId));
            }
        }

        List<RolePermEntry> instanceEntries = queryInstance(tenantId, roleIds, queryEntityIds, bitMasks);
        // T-PERM-058：同 passesScopeAll——子权限行仅在主资源上下文内生效，批量便捷入口不计入
        instanceEntries = instanceEntries.stream().filter(e -> e.dependOn() == null).toList();

        if (!instanceEntries.isEmpty()) {
            instanceEntries = conditionDomainService.evaluate(tenantId, instanceEntries, evalMap);
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
            if (entityId == null) {
                denied.add(entityId);
                continue;
            }
            Set<Long> closure = closureByTarget.getOrDefault(entityId, Set.of(entityId));
            boolean allowed = closure.stream().anyMatch(allowedEntityIds::contains);
            if (!allowed) {
                denied.add(entityId);
            }
        }
        return denied;
    }

    // ===== 私有步骤方法 =====

    /**
     * 预解析 ResolveContext（类型与操作，批量）
     */
    private ResolveContext prepareResolveContext(PermQuery q) {
        ResolveContext ctx = new ResolveContext(q.tenantId(), typeResolutionService);
        if (q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()) {
            ctx.prepareResourceTypes(q.resourceTypeCodes());
        }
        if (q.operationCodes() != null && !q.operationCodes().isEmpty()
            && q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()) {
            ctx.prepareOperations(q.resourceTypeCodes(), q.operationCodes());
        }
        return ctx;
    }

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
     * 解析操作ID集合为映射（loadAncillary 目标操作装配用，批量）
     */
    private Set<Long> resolveOperationIdsForAncillary(PermQuery q) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
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
     * 解析查询参数中的资源实体ID（INSTANCE 编码目标批量解析）
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
     * 判定面继承：目标集扩为 {目标}∪同类型祖先链（查询前，作用于实例查询目标下推）。
     */
    private Set<Long> expandTargetsByClosure(Long tenantId, Set<Long> targetEntityIds) {
        Set<Long> expanded = new LinkedHashSet<>(targetEntityIds);
        for (ResourceEntityMapper.AncestorClosureResult pair :
            resourceEntityMapper.selectSelfAndAncestorClosureBatch(tenantId, targetEntityIds)) {
            expanded.add(pair.getClosureId());
        }
        return expanded;
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
        if (bitMasks == null || bitMasks.isEmpty() || entityIds == null || entityIds.isEmpty()) {
            // 空目标集守卫（T-PERM-061）：空 entityIds 不下推 SQL——XML <if> 空集会静默丢掉
            // 实体过滤 = 无界全量行装载（旧路径只守空 bitMasks，grok 外评存量观察，设计列入改动面）
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
     * 按需评估条件和冲突（条件评估三态：评估 / 不评估 / 标记下发 markConditionsOnly）
     */
    private List<RolePermEntry> evaluateIfNeeded(PermQuery q, List<RolePermEntry> entries) {
        if (entries.isEmpty()) return entries;
        Map<String, Object> ctx = q.evalContext() == null ? Map.of() : q.evalContext().toEvalMap();
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
        Set<Long> targetOpIds = resolveOperationIdsForAncillary(q);
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
        return BusinessKeys.permEntrySourceKey(entry.permissionId(), entry.roleId(), entry.resourceEntityId(),
            entry.resourceType(), entry.operationBinaryBit(), entry.scopeAll());
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
        Map<Integer, Map<Long, OperationPermission>> opMapsByType =
            loadOperationIndexByType(tenantId, resourceTypes);

        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            Map<Long, OperationPermission> opMap = opMapsByType.getOrDefault(resourceType, Map.of());
            if (opMap.isEmpty()) {
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

    /**
     * 按资源类型批量装载操作索引（OPERATION_PERMISSIONS_BY_TYPE 缓存 + 冷 miss 批量回源）。
     * <p>
     * 单条掩码计算与批量判定（T-PERM-061）共享装载面：getBatch 批量读、miss 类型一次
     * 批量专属查询（IN）、putBatch 分组回填。
     * </p>
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @return resourceType → (operationId → OperationPermission)
     */
    private Map<Integer, Map<Long, OperationPermission>> loadOperationIndexByType(
        Long tenantId, Set<Integer> resourceTypes) {
        Map<Integer, Map<Long, OperationPermission>> result = new LinkedHashMap<>();
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return result;
        }
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
        for (Integer resourceType : resourceTypes) {
            result.put(resourceType, opMapsByCacheKey.getOrDefault(
                PermCacheCatalog.operationPermissionsByTypeKey(resourceType), Map.of()));
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

    // ===== 展示面展开（查询后条目克隆，不改变判定；T-PERM-057 归位为展示面轨道） =====

    /**
     * 展示面展开：按父子关系克隆结果条目（grantSource=INHERITED）。
     * <p>
     * 作用在查询后，不改变 allowed/denied 判定，只改变返回集合内容（清单面树扩展归口本轨道，
     * Q1 定案两语义拆分）。方向展开经目标下推递归 CTE（上溯止步同类型/软删截断/防环，
     * 下溯镜像 selectDescendantIdsBatch），不走 selectAllValid 全量图。
     * scopeAll 条目（resourceEntityId 为 null）不参与展开。
     * </p>
     *
     * @param tenantId         租户ID
     * @param entries          原始实例级权限条目
     * @param expandParents    是否向父方向克隆条目
     * @param expandChildren   是否向子方向克隆条目
     * @return 合并后的权限条目列表（原条目 + 展开条目）
     */
    private List<RolePermEntry> expandByPresentMode(Long tenantId, List<RolePermEntry> entries,
                                                     boolean expandParents, boolean expandChildren) {
        Set<Long> entityIds = entries.stream()
            .map(RolePermEntry::resourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (entityIds.isEmpty()) {
            return entries;
        }

        // 祖先展开：{条目实体}∪同类型祖先（排除自身）；子孙展开：全部后代
        Map<Long, List<Long>> ancestorsByEntity = Map.of();
        Map<Long, List<Long>> descendantsByEntity = Map.of();
        if (expandParents) {
            ancestorsByEntity = new LinkedHashMap<>();
            for (ResourceEntityMapper.AncestorClosureResult pair :
                resourceEntityMapper.selectSelfAndAncestorClosureBatch(tenantId, entityIds)) {
                if (!Objects.equals(pair.getTargetId(), pair.getClosureId())) {
                    ancestorsByEntity.computeIfAbsent(pair.getTargetId(), _unused -> new ArrayList<>())
                        .add(pair.getClosureId());
                }
            }
        }
        if (expandChildren) {
            descendantsByEntity = new LinkedHashMap<>();
            for (ResourceEntityMapper.DescendantResult pair :
                resourceEntityMapper.selectDescendantIdsBatch(tenantId, entityIds)) {
                descendantsByEntity.computeIfAbsent(pair.getResourceId(), _unused -> new ArrayList<>())
                    .add(pair.getDescendantId());
            }
        }

        List<RolePermEntry> expandedEntries = new ArrayList<>();
        Set<String> expandedKeys = new HashSet<>(); // 去重：entityId+"|"+permissionId
        for (RolePermEntry entry : entries) {
            Long srcEntityId = entry.resourceEntityId();
            if (srcEntityId == null) {
                continue; // scopeAll 条目不参与展开
            }
            for (Long ancestorId : ancestorsByEntity.getOrDefault(srcEntityId, List.of())) {
                String key = BusinessKeys.inheritedEntryKey(ancestorId, entry.permissionId());
                if (expandedKeys.add(key)) {
                    expandedEntries.add(cloneWithInherited(entry, ancestorId));
                }
            }
            for (Long descendantId : descendantsByEntity.getOrDefault(srcEntityId, List.of())) {
                String key = BusinessKeys.inheritedEntryKey(descendantId, entry.permissionId());
                if (expandedKeys.add(key)) {
                    expandedEntries.add(cloneWithInherited(entry, descendantId));
                }
            }
        }

        List<RolePermEntry> result = new ArrayList<>(entries);
        result.addAll(expandedEntries);
        return result;
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

    // ===== LIST 专用方法 =====

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
    private List<RolePermEntry> loadRolePermEntriesWithCache(Long tenantId, Set<Long> roleIds, boolean bypassSnapshot) {
        if (bypassSnapshot) {
            // 写校验面（canGrant）：直查不回填——ROLE_PERM_SNAPSHOT 10s TTL 陈旧窗口与
            // 旧读回填竞态对授权传递校验不可接受（codex 外评 P1）
            return rolePermMapper.selectValidByRoleIds(tenantId, roleIds).stream()
                .map(entryMapper::toEntry).toList();
        }
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
                                       List<RolePermEntry> entries,
                                       List<RolePermEntry> operationsScopeEntries, Set<Long> roleIds) {
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
            // 操作定义装载源=operationsScopeEntries（LIST 传 rawEntries 超集）——资源/投影仍按
            // 评估后条目 entries；被条件摘光的类型其操作定义必须在场（四态组装 EMPTY 分态依赖）
            Set<Integer> allResourceTypes = operationsScopeEntries.stream()
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
