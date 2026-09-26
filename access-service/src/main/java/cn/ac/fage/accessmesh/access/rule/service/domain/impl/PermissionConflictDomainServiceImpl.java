package cn.ac.fage.accessmesh.access.rule.service.domain.impl;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.rule.enums.ConflictType;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限冲突领域服务实现类
 * <p>
 * 提供权限冲突检测和处理功能。
 * 支持两种冲突类型：
 * - ROLE_MUTEX（角色互斥）：两个角色不能同时拥有，发生冲突时同时移除——
 *   S/H-D 全命中确定化（T-PERM-083，2026-09-25 拍板）：对原始集一次算全部命中对、
 *   端点并集一次删净，顺序无关、无图连通传递删除
 * - PERM_MUTEX（权限互斥）：两个操作权限不能同时授予，发生冲突时同时移除——
 *   真实命中规则 AND 判定直返，空规则短路零装载（T-PERM-083）
 * 纯计算（{@link #computeRoleMutex}/{@link #computePermMutex}）与通知解耦：
 * 判定入口包装叠加去重通知，新核心消费纯计算自管通知（设计 §5.1/§6.1）。
 * 角色互斥规则通过统一 CacheService 缓存提高查询性能。
 * 检测到权限冲突时，记录操作日志并发出通知（日志写入经 AuditDomainService
 * 有界线程池异步执行，不阻塞主流程）。
 * </p>
 */
@Service
public class PermissionConflictDomainServiceImpl implements PermissionConflictDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionConflictDomainServiceImpl.class);

    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final AuditDomainService auditDomainService;
    private final OperationPermissionDomainService operationPermissionDomainService;
    private final SubjectDomainService subjectDomainService;

    /**
     * 双删日志去重表（T-PERM-063）：每「租户×用户×角色对」每 JVM 1 小时至多一条
     * CONFLICT_DETECTED。快照链路是高频读路径，无去重会随 TTL 过期反复刷屏；
     * 有界（10000 条）+ expireAfterWrite 自淘汰，多实例各自独立记账。
     */
    private final Cache<String, Boolean> mutexDropNotified = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofHours(1))
        .build();

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleMapper        权限冲突规则数据访问层
     * @param cacheService              统一缓存服务，用于缓存角色互斥规则
     * @param objectMapper              JSON解析器
     * @param auditDomainService        审计领域服务，用于记录冲突通知
     * @param operationPermissionDomainService 操作定义事实领域服务，用于查找冲突操作权限（Q-009 收敛注入）
     * @param subjectDomainService      主体领域服务，用于按角色反查用户与有效角色解析（存量双持判定与运行时同源）
     */
    public PermissionConflictDomainServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                                CacheService cacheService,
                                                ObjectMapper objectMapper,
                                                AuditDomainService auditDomainService,
                                                OperationPermissionDomainService operationPermissionDomainService,
                                                SubjectDomainService subjectDomainService) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.auditDomainService = auditDomainService;
        this.operationPermissionDomainService = operationPermissionDomainService;
        this.subjectDomainService = subjectDomainService;
    }

    /**
     * 过滤角色互斥冲突（S/H-D 全命中确定化，T-PERM-083）
     * <p>
     * 内部复用 {@link #computeRoleMutex} 纯计算，叠加双删通知（每命中对一条，
     * 去重限流见 {@link #mutexDropNotified}）——此前双删静默无痕，用户权限消失无任何可查记录。
     * </p>
     *
     * @param tenantId        租户ID
     * @param userId          用户ID（快照构建方已知，日志归因用）
     * @param effectiveRoleIds 有效角色ID集合
     * @return 过滤后的有效角色ID集合（移除互斥角色）
     */
    @Override
    public Set<Long> filterRoleMutex(Long tenantId, Long userId, Set<Long> effectiveRoleIds) {
        RoleMutexComputation computation = computeRoleMutex(tenantId, effectiveRoleIds);
        // S/H-D 对原始集算全部命中对：链式双持各对都通知（旧顺序遍历第二对不再两端在场漏报的形态终结）
        for (RolePairRef hit : computation.hits()) {
            notifyRoleMutexDrop(tenantId, userId, hit);
        }
        return computation.keptRoleIds();
    }

    /**
     * S/H-D 纯角色互斥计算（T-PERM-083，2026-09-25 拍板定案算法；不通知）。
     * <p>
     * H = 规则两端都在原始集 S 的全部命中对；D = H 端点并集；结果 = S − D——
     * 一次算全、一次删净：规则顺序无关（旧顺序遍历边删边判的顺序依赖终结，
     * registry 2026-09-22 留观②闭合）、仅持单端不成对不删（无图连通传递删除）。
     * 删多为预期收紧（灰度差异按设计 §10.5 预期修复登记，非回归）。
     * </p>
     */
    @Override
    public RoleMutexComputation computeRoleMutex(Long tenantId, Set<Long> effectiveRoleIds) {
        List<RoleMutexPair> mutexPairs = loadMutexPairs(tenantId);
        List<RolePairRef> hits = new ArrayList<>();
        Set<Long> dropped = new HashSet<>();
        for (RoleMutexPair pair : mutexPairs) {
            if (pair.first() != null && pair.second() != null
                && effectiveRoleIds.contains(pair.first()) && effectiveRoleIds.contains(pair.second())) {
                hits.add(new RolePairRef(pair.first(), pair.second()));
                dropped.add(pair.first());
                dropped.add(pair.second());
            }
        }
        // LinkedHashSet 而非 Set.copyOf：SetN 的迭代起点按 JVM 级随机盐旋转（跨重启顺序漂移），
        // 下游 allEntries 按角色迭代序拼接进响应数组与分页切片——保持跨运行确定序（claude 外评 P3-1）
        Set<Long> kept = new LinkedHashSet<>(effectiveRoleIds);
        kept.removeAll(dropped);
        return new RoleMutexComputation(Collections.unmodifiableSet(kept), hits);
    }

    /**
     * 角色互斥规则装载（ROLE_MUTEX_RULE 缓存 JSON 优先，miss/损坏回源 DB 并回填）。
     */
    private List<RoleMutexPair> loadMutexPairs(Long tenantId) {
        String cachedJson = cacheService.get(AccessCacheCatalog.ROLE_MUTEX_RULE, tenantId, "all");

        if (cachedJson != null) {
            try {
                List<Map<String, Long>> cachedRules = objectMapper.readValue(cachedJson,
                    new TypeReference<List<Map<String, Long>>>() {});
                return cachedRules.stream()
                    .map(m -> new RoleMutexPair(m.get("first"), m.get("second")))
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to parse cached mutex rules, fallback to DB: tenantId={}", tenantId);
            }
        }
        return loadMutexRulesFromDb(tenantId);
    }

    /**
     * 共同判定语义入口（T-PERM-075）：有效角色解析 + 互斥双删一次完成。
     * <p>
     * check / batch-check / validate / scope / 快照 / 菜单视图的统一消费点；
     * 双删审计语义沿 {@link #filterRoleMutex}（去重限流同表）。
     * </p>
     */
    @Override
    public Set<Long> resolveJudgementRoleIds(Long tenantId, Long userId) {
        Set<Long> effectiveRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        if (effectiveRoleIds.isEmpty()) {
            return Set.of();
        }
        return filterRoleMutex(tenantId, userId, effectiveRoleIds);
    }

    /**
     * 双删日志（T-PERM-063）：异步记录 CONFLICT_DETECTED，去重限流。
     * <p>
     * 审计写入经 {@link AuditDomainService#asyncRecordLog} 有界线程池异步执行，不阻塞快照链路。
     * 证据=角色对（{@link RolePairRef}，缓存仅存角色对不虚构 ruleId，T-PERM-083）。
     * </p>
     */
    private void notifyRoleMutexDrop(Long tenantId, Long userId, RolePairRef pair) {
        String dedupKey = tenantId + ":" + userId + ":" + pair.firstRoleId() + ":" + pair.secondRoleId();
        if (mutexDropNotified.getIfPresent(dedupKey) != null) {
            return;
        }
        mutexDropNotified.put(dedupKey, Boolean.TRUE);
        try {
            auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
                tenantId, "PERMISSION", "CONFLICT_DETECTED", "permission_conflict_rule", null,
                String.format("Role mutex dropped: tenantId=%d, userId=%d, roles=%d vs %d "
                    + "(both roles removed from effective set at judgement, T-PERM-075 unified entries)",
                    tenantId, userId, pair.firstRoleId(), pair.secondRoleId()),
                null, null, null, OperatorContext.getRequestId(), null, null, null
            ));
        } catch (Exception e) {
            // 仅提交期异常（如线程池拒绝）回滚去重标记允许窗口内重试（外评 P3 补充口径）；
            // asyncRecordLog 经 @Async 代理立即返回，异步线程内的落库失败不经此 catch，
            // 由 AsyncUncaughtExceptionHandler 告警通道兜底——去重标记维持，窗口后自然重试
            mutexDropNotified.invalidate(dedupKey);
            log.error("Failed to record role mutex drop notification: tenantId={}, userId={}", tenantId, userId, e);
        }
    }

    /**
     * 授予前互斥冲突检测（T-PERM-063 写路径校验；T-PERM-075 区间交语义）。
     * <p>
     * 规则 DB 直查（不经缓存），新建规则即刻生效于授予校验。
     * 冲突判定：两个互斥角色各自任一持有窗口重叠（闭区间，null=无限端）才命中——
     * 真正不相交的未来窗口放行（U002-1 拍板）。
     * </p>
     */
    @Override
    public List<RoleMutexAssignConflict> findAssignMutexConflicts(
        Long tenantId, Map<Long, Set<SubjectDomainService.RawHolding>> holdingsByUser) {
        return findAssignMutexConflicts(tenantId, holdingsByUser, null);
    }

    /**
     * 授予前互斥冲突检测（T-PERM-063 写路径校验；T-PERM-075 区间交语义）。
     * <p>
     * 规则 DB 直查（不经缓存），新建规则即刻生效于授予校验；批级调用方
     * （full-sync）经预载重载复用循环前装载的规则集（claude 外评 P3-1）。
     * 冲突判定：两个互斥角色各自任一持有窗口重叠（闭区间，null=无限端，
     * 倒置窗口视为空窗）才命中——真正不相交的未来窗口放行（U002-1 拍板）。
     * </p>
     */
    @Override
    public List<RoleMutexAssignConflict> findAssignMutexConflicts(
        Long tenantId, Map<Long, Set<SubjectDomainService.RawHolding>> holdingsByUser,
        List<PermissionConflictRule> preloadedRules) {
        if (holdingsByUser == null || holdingsByUser.isEmpty()) {
            return List.of();
        }
        List<PermissionConflictRule> rules = preloadedRules != null
            ? preloadedRules
            : conflictRuleMapper.selectByConflictType(tenantId, ConflictType.ROLE_MUTEX.getValue());
        if (rules.isEmpty()) {
            return List.of();
        }
        List<RoleMutexAssignConflict> conflicts = new ArrayList<>();
        for (Map.Entry<Long, Set<SubjectDomainService.RawHolding>> entry : holdingsByUser.entrySet()) {
            Set<SubjectDomainService.RawHolding> holdings = entry.getValue();
            if (holdings == null || holdings.size() < 2) {
                continue;
            }
            for (PermissionConflictRule rule : rules) {
                if (rule.getFirstAbstractRoleId() != null && rule.getSecondAbstractRoleId() != null
                    && hasOverlappingWindows(holdings, rule.getFirstAbstractRoleId(), rule.getSecondAbstractRoleId())) {
                    conflicts.add(new RoleMutexAssignConflict(
                        entry.getKey(), rule.getId(),
                        rule.getFirstAbstractRoleId(), rule.getSecondAbstractRoleId()));
                }
            }
        }
        return conflicts;
    }

    @Override
    public List<PermissionConflictRule> loadRoleMutexRulesFresh(Long tenantId) {
        return conflictRuleMapper.selectByConflictType(tenantId, ConflictType.ROLE_MUTEX.getValue());
    }

    /**
     * 窗口区间交判定：角色 X 与 Y 各自任一持有窗口重叠即冲突。
     * <p>
     * 闭区间语义（对齐运行时 selectValidByUserIdsWithValidity 谓词系）：
     * a.to >= b.from && b.to >= a.from，null 端=无穷——首尾相接（a.to == b.from）当天同刻有效算重叠；
     * 两窗口真正不相交（[m,n] 与 [n+ε,k]）放行。
     * </p>
     */
    private boolean hasOverlappingWindows(Set<SubjectDomainService.RawHolding> holdings,
                                          Long firstRoleId, Long secondRoleId) {
        // 倒置窗口（valid_from > valid_to）在运行时谓词下恒假（永不生效、不触发双删），
        // 判定前剔除——写守卫与运行时对同一数据不得给出相反结论（claude 外评 P3-2）
        List<SubjectDomainService.RawHolding> firstWindows = new ArrayList<>();
        List<SubjectDomainService.RawHolding> secondWindows = new ArrayList<>();
        for (SubjectDomainService.RawHolding holding : holdings) {
            if (neverEffective(holding)) {
                continue;
            }
            if (firstRoleId.equals(holding.roleId())) {
                firstWindows.add(holding);
            } else if (secondRoleId.equals(holding.roleId())) {
                secondWindows.add(holding);
            }
        }
        for (SubjectDomainService.RawHolding a : firstWindows) {
            for (SubjectDomainService.RawHolding b : secondWindows) {
                boolean aEndsAfterBStarts = a.validTo() == null || b.validFrom() == null
                    || !a.validTo().isBefore(b.validFrom());
                boolean bEndsAfterAStarts = b.validTo() == null || a.validFrom() == null
                    || !b.validTo().isBefore(a.validFrom());
                if (aEndsAfterBStarts && bEndsAfterAStarts) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 倒置窗口（from > to）：运行时谓词恒假，等同空窗不参与重叠判定。 */
    private boolean neverEffective(SubjectDomainService.RawHolding holding) {
        return holding.validFrom() != null && holding.validTo() != null
            && holding.validFrom().isAfter(holding.validTo());
    }

    /**
     * 存量双持查询（T-PERM-063 规则写路径守卫；T-PERM-075 口径扩展）。
     * <p>
     * 候选超集按角色三路反查（含组角色间接持有），收敛到原始持有窗口做区间交判定
     * （未过期含未来窗口、含禁用持有与禁用组子树，继承组绑定行窗口）——与全部
     * 用户-角色写守卫同口径（U002 写时堵死），消除「绑定时拒、立规时放」双通道不一致；
     * 窗口真正不相交的双持不计。
     * </p>
     */
    @Override
    public List<Long> findUsersHoldingBothRoles(Long tenantId, Long firstRoleId, Long secondRoleId) {
        Set<Long> candidates = subjectDomainService.findUserIdsByEffectiveRoles(
            tenantId, Set.of(firstRoleId, secondRoleId));
        if (candidates.isEmpty()) {
            return List.of();
        }
        // T-PERM-075：收敛到原始持有窗口（未过期含未来窗口、含禁用持有与禁用组子树），
        // 按区间交判定双持——与用户-角色全部写守卫同口径，消除「绑定时拒、立规时放」的双通道不一致
        Map<Long, Set<SubjectDomainService.RawHolding>> rawHoldings =
            subjectDomainService.batchResolveRawHoldings(tenantId, candidates);
        List<Long> holders = new ArrayList<>();
        for (Long userId : candidates) {
            if (hasOverlappingWindows(rawHoldings.getOrDefault(userId, Set.of()), firstRoleId, secondRoleId)) {
                holders.add(userId);
            }
        }
        return holders;
    }

    /**
     * 从数据库加载角色互斥规则并缓存
     * <p>
     * T-ACCESS-008：DB 读取前记录读取起点，回填只写剩余 TTL。
     * </p>
     */
    private List<RoleMutexPair> loadMutexRulesFromDb(Long tenantId) {
        CacheReadToken<String> readToken = cacheService.beginRead(AccessCacheCatalog.ROLE_MUTEX_RULE);
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(
            tenantId, ConflictType.ROLE_MUTEX.getValue());
        List<RoleMutexPair> mutexPairs = rules.stream()
            .map(r -> new RoleMutexPair(r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId()))
            .collect(Collectors.toList());

        // 回填缓存（JSON格式，剩余 TTL；T-PERM-075：空规则集也缓存——防穿透。
        // 互斥过滤统一进全部判定入口后，空规则租户的每次判定都会读本缓存，
        // 不缓存空集会使判定路径每次打 DB。立规后运行时沿 10s TTL 收敛，
        // 写守卫（findAssignMutexConflicts/findUsersHoldingBothRoles）DB 直查不受影响）
        try {
            List<Map<String, Long>> toCache = mutexPairs.stream()
                .map(p -> Map.of("first", p.first(), "second", p.second()))
                .collect(Collectors.toList());
            String json = objectMapper.writeValueAsString(toCache);
            cacheService.put(readToken, tenantId, "all", json);
        } catch (Exception e) {
            log.warn("Failed to serialize mutex rules for caching: tenantId={}", tenantId);
        }
        return mutexPairs;
    }

    /**
     * 过滤权限互斥冲突
     * <p>
     * 内部与公开入口 {@link #computePermMutex} 共用同一计算体（私有
     * {@code computePermMutexInternal}）；检测到冲突时异步通知——
     * 明细由真实命中规则（AND 两端在场）构造，不按冲突端点反推（T-PERM-083）。
     * </p>
     *
     * @param tenantId     租户ID
     * @param passedEntries 通过初步检查的权限条目列表
     * @return 过滤后的权限条目列表（移除互斥权限）
     */
    @Override
    public List<RolePermEntry> filterPermMutex(Long tenantId, List<RolePermEntry> passedEntries) {
        PermMutexComputationInternal computation = computePermMutexInternal(tenantId, passedEntries);
        if (!computation.triggeredRules().isEmpty()) {
            notifyPermConflict(tenantId, computation.conflictingOpIds(), computation.triggeredRules());
        }
        return computation.keptEntries();
    }

    /**
     * PERM_MUTEX 单路径纯计算（T-PERM-083，不通知；空规则短路零装载）。
     * <p>
     * 与批量评估器 {@link BatchPermMutexEvaluator} 同剔除语义：条目 grantedBits 经
     * (resourceType, binaryBit) 精确查表解析操作，规则两端都在条目操作 ID 集合中时
     * 两端全部剔除；真实 triggeredRuleIds 由 AND 判定直返（设计 §5.1）。
     * </p>
     */
    @Override
    public BatchPermMutexEvaluator.PermMutexComputation computePermMutex(Long tenantId, List<RolePermEntry> passedEntries) {
        PermMutexComputationInternal computation = computePermMutexInternal(tenantId, passedEntries);
        return new BatchPermMutexEvaluator.PermMutexComputation(
            computation.keptEntries(), computation.triggeredRuleIds());
    }

    @Override
    public BatchPermMutexEvaluator openBatchMutexEvaluator(Long tenantId) {
        return new BatchPermMutexEvaluatorImpl(tenantId);
    }

    /**
     * 请求级批量互斥评估器（T-PERM-061：静态数据共享装载 + 计算通知解耦）。
     * <p>
     * 规则惰性装载一次、操作索引按 distinct 类型惰性扩；剔除语义与
     * {@link #filterPermMutex}（内部 {@code computePermMutexInternal}）逐分支一致
     * （同款精确查表与两端同场判定），但不通知——通知由调用方 ledger 聚合后
     * 经 {@link #notifyHits} 触发（b2 定案）。
     * </p>
     */
    private final class BatchPermMutexEvaluatorImpl implements BatchPermMutexEvaluator {

        private final Long tenantId;
        private List<PermissionConflictRule> rules;
        private Map<Long, PermissionConflictRule> ruleById = Map.of();
        /** (resourceType, binaryBit) → 操作索引，按已见类型惰性扩（distinct 类型 O(K)） */
        private final Map<String, OperationPermission> opByTypeAndBit = new LinkedHashMap<>();
        private final Set<Integer> indexedTypes = new HashSet<>();

        BatchPermMutexEvaluatorImpl(Long tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public PermMutexComputation compute(List<RolePermEntry> entries) {
            if (entries == null || entries.isEmpty()) {
                return new PermMutexComputation(List.of(), Set.of());
            }
            ensureRulesLoaded();
            ensureOperationIndex(entries);
            // 剔除语义与单条路径一致（集合语义：对子集整体算 opIds，两端同场才冲突且两端全丢）
            Set<Long> opIds = entries.stream()
                .map(entry -> OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                    opByTypeAndBit, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull)
                .map(OperationPermission::getId)
                .collect(Collectors.toSet());
            Set<Long> triggeredRuleIds = new LinkedHashSet<>();
            Set<Long> conflictingOpIds = new HashSet<>();
            for (PermissionConflictRule rule : rules) {
                if (rule.getFirstOperationPermissionId() != null && rule.getSecondOperationPermissionId() != null
                    && opIds.contains(rule.getFirstOperationPermissionId())
                    && opIds.contains(rule.getSecondOperationPermissionId())) {
                    triggeredRuleIds.add(rule.getId());
                    conflictingOpIds.add(rule.getFirstOperationPermissionId());
                    conflictingOpIds.add(rule.getSecondOperationPermissionId());
                }
            }
            List<RolePermEntry> filtered = entries.stream()
                .filter(entry -> {
                    OperationPermission granted = OperationPermissionUtils
                        .findIndexedByResourceTypeAndBinaryBit(opByTypeAndBit,
                            entry.resourceType(), entry.grantedBits());
                    return granted == null || !conflictingOpIds.contains(granted.getId());
                })
                .collect(Collectors.toList());
            return new PermMutexComputation(filtered, triggeredRuleIds);
        }

        @Override
        public void notifyHits(Long tenantId, List<MutexHit> hits) {
            if (hits == null || hits.isEmpty()) {
                return;
            }
            ensureRulesLoaded();
            for (MutexHit hit : hits) {
                // detail 由实际命中规则（AND 两端在场）构造——未触发规则不出现（内容锁口径）
                PermissionConflictRule rule = hit.ruleId() == null ? null : ruleById.get(hit.ruleId());
                if (rule == null) {
                    log.warn("Batch mutex hit references unknown rule, skipped: tenantId={}, group={}, ruleId={}",
                        tenantId, hit.groupKey(), hit.ruleId());
                    continue;
                }
                String detail = String.format("rule[%d]: op%d vs op%d", rule.getId(),
                    rule.getFirstOperationPermissionId(), rule.getSecondOperationPermissionId());
                try {
                    auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
                        tenantId, "PERMISSION", "CONFLICT_DETECTED", "permission_conflict_rule", null,
                        String.format("Perm conflict blocked (batch): tenantId=%d, group=%s, hitItemCount=%d, detail=%s",
                            tenantId, hit.groupKey(), hit.hitItemCount(), detail),
                        null, null, null, OperatorContext.getRequestId(), null, null, null
                    ));
                } catch (Exception e) {
                    log.error("Failed to record batch permission conflict notification: tenantId={}, group={}",
                        tenantId, hit.groupKey(), e);
                }
            }
        }

        private void ensureRulesLoaded() {
            if (rules != null) {
                return;
            }
            rules = conflictRuleMapper.selectByConflictType(tenantId, ConflictType.PERM_MUTEX.getValue());
            Map<Long, PermissionConflictRule> index = new LinkedHashMap<>();
            for (PermissionConflictRule rule : rules) {
                index.put(rule.getId(), rule);
            }
            ruleById = Map.copyOf(index);
        }

        private void ensureOperationIndex(List<RolePermEntry> entries) {
            Set<Integer> missing = entries.stream()
                .map(RolePermEntry::resourceType)
                .filter(Objects::nonNull)
                .filter(type -> !indexedTypes.contains(type))
                .collect(Collectors.toSet());
            if (missing.isEmpty()) {
                return;
            }
            indexedTypes.addAll(missing);
            List<OperationPermission> loaded = operationPermissionDomainService
                .selectByTenantAndResourceTypes(tenantId, missing);
            opByTypeAndBit.putAll(OperationPermissionUtils.indexByResourceTypeAndBinaryBit(loaded));
        }
    }


    /**
     * PERM_MUTEX 单路径计算体：操作索引装载、AND 命中规则收集、条目剔除（T-PERM-083）。
     */
    private PermMutexComputationInternal computePermMutexInternal(Long tenantId, List<RolePermEntry> passedEntries) {
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(
            tenantId, ConflictType.PERM_MUTEX.getValue());

        // I01（T-PERM-083，设计 §5.1）：成功读到空规则即短路——互斥专用操作目录零装载
        // （旧实现空规则租户每次判定仍按条目类型装载操作目录，纯开销）
        if (rules.isEmpty()) {
            return new PermMutexComputationInternal(new ArrayList<>(passedEntries), Set.of(), List.of(), Set.of());
        }

        Set<Integer> resourceTypes = passedEntries.stream()
            .map(RolePermEntry::resourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<OperationPermission> allOps = operationPermissionDomainService
            .selectByTenantAndResourceTypes(tenantId, resourceTypes);
        Map<String, OperationPermission> opByTypeAndBit = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(allOps);

        Set<Long> opIds = passedEntries.stream()
            .map(entry -> OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                opByTypeAndBit,
                entry.resourceType(),
                entry.grantedBits()
            ))
            .filter(Objects::nonNull)
            .map(OperationPermission::getId)
            .collect(Collectors.toSet());

        // 真实命中规则一次收集（AND 两端在场）：triggeredRuleIds 直返、通知明细不反推（T-PERM-083）
        Set<Long> triggeredRuleIds = new LinkedHashSet<>();
        List<PermissionConflictRule> triggeredRules = new ArrayList<>();
        Set<Long> conflictingOpIds = new HashSet<>();
        for (PermissionConflictRule rule : rules) {
            if (rule.getFirstOperationPermissionId() != null && rule.getSecondOperationPermissionId() != null
                && opIds.contains(rule.getFirstOperationPermissionId())
                && opIds.contains(rule.getSecondOperationPermissionId())) {
                triggeredRuleIds.add(rule.getId());
                triggeredRules.add(rule);
                conflictingOpIds.add(rule.getFirstOperationPermissionId());
                conflictingOpIds.add(rule.getSecondOperationPermissionId());
            }
        }

        List<RolePermEntry> keptEntries = passedEntries.stream()
            .filter(entry -> {
                OperationPermission granted = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                    opByTypeAndBit, entry.resourceType(), entry.grantedBits());
                return granted == null || !conflictingOpIds.contains(granted.getId());
            })
            .collect(Collectors.toList());
        return new PermMutexComputationInternal(keptEntries, triggeredRuleIds, triggeredRules, conflictingOpIds);
    }

    /**
     * PERM_MUTEX 单路径计算结果（内部富形态：公开面见 {@link #computePermMutex}）。
     */
    private record PermMutexComputationInternal(
        List<RolePermEntry> keptEntries,
        Set<Long> triggeredRuleIds,
        List<PermissionConflictRule> triggeredRules,
        Set<Long> conflictingOpIds
    ) {}

    /**
     * 角色互斥对内部记录类
     * <p>
     * 用于存储互斥的两个角色ID。
     * </p>
     */
    private record RoleMutexPair(Long first, Long second) {}

    /**
     * 通知权限冲突（记录冲突操作日志）。
     * <p>
     * 检测到权限互斥冲突时记录冲突的租户ID、操作权限ID集合、触发规则详情。
     * detail 由真实命中规则（AND 两端在场，计算期收集）构造——不按冲突端点 OR 反推：
     * 旧反推会把仅单端恰好落在冲突操作集的未触发规则也列入明细（T-PERM-083 终结）。
     * 本方法由同类方法内调用（self-invocation），无 Spring 代理，故不再标注
     * {@code @Async}（标注了也不生效）；"异步"职责统一归到底层
     * {@link AuditDomainService#asyncRecordLog}（其自身 {@code @Async} + REQUIRES_NEW
     * 在有界线程池异步写入）。本方法体仅同步组装日志条目后提交，不阻塞主流程。
     * </p>
     *
     * @param tenantId         租户ID
     * @param conflictingOpIds 冲突的操作权限ID集合
     * @param triggeredRules   真实触发的冲突规则列表（AND 判定收集，非反推）
     */
    void notifyPermConflict(Long tenantId, Set<Long> conflictingOpIds, List<PermissionConflictRule> triggeredRules) {
        try {
            String detail = triggeredRules.stream()
                .map(r -> String.format("rule[%d]: op%d vs op%d", r.getId(),
                    r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId()))
                .collect(Collectors.joining("; "));
            log.warn("Permission conflict detected: tenantId={}, conflictingOps={}, rules={}",
                tenantId, conflictingOpIds, detail);
            auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
                tenantId, "PERMISSION", "CONFLICT_DETECTED", "permission_conflict_rule", null,
                String.format("Perm conflict blocked: tenantId=%d, ops=%s, detail=%s",
                    tenantId, conflictingOpIds, detail),
                null, null, null, OperatorContext.getRequestId(), null, null, null
            ));
        } catch (Exception e) {
            log.error("Failed to record permission conflict notification: tenantId={}", tenantId, e);
        }
    }
}