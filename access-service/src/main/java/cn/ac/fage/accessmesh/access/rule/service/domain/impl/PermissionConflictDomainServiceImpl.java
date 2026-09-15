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
 * - ROLE_MUTEX（角色互斥）：两个角色不能同时拥有，发生冲突时同时移除
 * - PERM_MUTEX（权限互斥）：两个操作权限不能同时授予，发生冲突时同时移除
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
     * 过滤角色互斥冲突
     * <p>
     * 根据角色互斥规则过滤有效角色集合。
     * 如果用户同时拥有互斥的两个角色，则同时移除这两个角色。
     * 角色互斥规则通过 CacheService 缓存（JSON格式）。
     * 双删命中时记录 CONFLICT_DETECTED 操作日志（T-PERM-063，对齐 PERM_MUTEX 先例；
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
        // 从缓存获取角色互斥规则（JSON格式）
        String cachedJson = cacheService.get(AccessCacheCatalog.ROLE_MUTEX_RULE, tenantId, "all");

        List<RoleMutexPair> mutexPairs;
        if (cachedJson != null) {
            try {
                List<Map<String, Long>> cachedRules = objectMapper.readValue(cachedJson,
                    new TypeReference<List<Map<String, Long>>>() {});
                mutexPairs = cachedRules.stream()
                    .map(m -> new RoleMutexPair(m.get("first"), m.get("second")))
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to parse cached mutex rules, fallback to DB: tenantId={}", tenantId);
                mutexPairs = loadMutexRulesFromDb(tenantId);
            }
        } else {
            mutexPairs = loadMutexRulesFromDb(tenantId);
        }

        Set<Long> result = new HashSet<>(effectiveRoleIds);
        for (RoleMutexPair pair : mutexPairs) {
            if (pair.first() != null && pair.second() != null
                && result.contains(pair.first()) && result.contains(pair.second())) {
                result.remove(pair.first());
                result.remove(pair.second());
                notifyRoleMutexDrop(tenantId, userId, pair);
            }
        }
        return result;
    }

    /**
     * 双删日志（T-PERM-063）：异步记录 CONFLICT_DETECTED，去重限流。
     * <p>
     * 审计写入经 {@link AuditDomainService#asyncRecordLog} 有界线程池异步执行，不阻塞快照链路。
     * </p>
     */
    private void notifyRoleMutexDrop(Long tenantId, Long userId, RoleMutexPair pair) {
        String dedupKey = tenantId + ":" + userId + ":" + pair.first() + ":" + pair.second();
        if (mutexDropNotified.getIfPresent(dedupKey) != null) {
            return;
        }
        mutexDropNotified.put(dedupKey, Boolean.TRUE);
        try {
            auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
                tenantId, "PERMISSION", "CONFLICT_DETECTED", "permission_conflict_rule", null,
                String.format("Role mutex dropped: tenantId=%d, userId=%d, roles=%d vs %d "
                    + "(both roles removed from effective set at snapshot build)",
                    tenantId, userId, pair.first(), pair.second()),
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
     * 授予前互斥冲突检测（T-PERM-063 写路径校验）。
     * <p>
     * 规则 DB 直查（不经缓存），新建规则即刻生效于授予校验。
     * 冲突判定与运行时 filterRoleMutex 同语义：集合同时含两端即命中。
     * </p>
     */
    @Override
    public List<RoleMutexAssignConflict> findAssignMutexConflicts(Long tenantId,
                                                                  Map<Long, Set<Long>> postStateRoleIdsByUser) {
        if (postStateRoleIdsByUser == null || postStateRoleIdsByUser.isEmpty()) {
            return List.of();
        }
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(
            tenantId, ConflictType.ROLE_MUTEX.getValue());
        if (rules.isEmpty()) {
            return List.of();
        }
        List<RoleMutexAssignConflict> conflicts = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : postStateRoleIdsByUser.entrySet()) {
            Set<Long> postState = entry.getValue();
            if (postState == null || postState.size() < 2) {
                continue;
            }
            for (PermissionConflictRule rule : rules) {
                if (rule.getFirstAbstractRoleId() != null && rule.getSecondAbstractRoleId() != null
                    && postState.contains(rule.getFirstAbstractRoleId())
                    && postState.contains(rule.getSecondAbstractRoleId())) {
                    conflicts.add(new RoleMutexAssignConflict(
                        entry.getKey(), rule.getId(),
                        rule.getFirstAbstractRoleId(), rule.getSecondAbstractRoleId()));
                }
            }
        }
        return conflicts;
    }

    /**
     * 存量双持查询（T-PERM-063 规则写路径守卫）。
     * <p>
     * 候选超集经 {@link SubjectDomainService#findUserIdsByEffectiveRoles} 按角色反查用户
     * （ROLE 直授 + GROUP_ROLE 直绑 + 祖先组展开三路——组角色间接持有同入候选，
     * 双轨评审 P1-1：直授行查询会漏经组展开的持有），再经
     * {@link SubjectDomainService#batchResolveEffectiveRoles} 收敛到有效角色集做 AND 判定
     * （覆盖有效性窗口、启用态、组角色展开）——与运行时 filterRoleMutex 的判定集合同源，
     * 避免把已过期/已禁用关系的持有误计为存量违规。
     * </p>
     */
    @Override
    public List<Long> findUsersHoldingBothRoles(Long tenantId, Long firstRoleId, Long secondRoleId) {
        Set<Long> candidates = subjectDomainService.findUserIdsByEffectiveRoles(
            tenantId, Set.of(firstRoleId, secondRoleId));
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Set<Long>> effective = subjectDomainService.batchResolveEffectiveRoles(tenantId, candidates);
        List<Long> holders = new ArrayList<>();
        for (Long userId : candidates) {
            Set<Long> roles = effective.getOrDefault(userId, Set.of());
            if (roles.contains(firstRoleId) && roles.contains(secondRoleId)) {
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

        // 回填缓存（JSON格式，剩余 TTL）
        if (!mutexPairs.isEmpty()) {
            try {
                List<Map<String, Long>> toCache = mutexPairs.stream()
                    .map(p -> Map.of("first", p.first(), "second", p.second()))
                    .collect(Collectors.toList());
                String json = objectMapper.writeValueAsString(toCache);
                cacheService.put(readToken, tenantId, "all", json);
            } catch (Exception e) {
                log.warn("Failed to serialize mutex rules for caching: tenantId={}", tenantId);
            }
        }
        return mutexPairs;
    }

    /**
     * 过滤权限互斥冲突
     * <p>
     * 根据权限互斥规则过滤权限条目列表。
     * 如果用户同时拥有互斥的两个操作权限，则同时移除这两个权限。
     * 检测到权限冲突时，异步发出通知并记录操作日志。
     * </p>
     *
     * @param tenantId     租户ID
     * @param passedEntries 通过初步检查的权限条目列表
     * @return 过滤后的权限条目列表（移除互斥权限）
     */
    @Override
    public List<RolePermEntry> filterPermMutex(Long tenantId, List<RolePermEntry> passedEntries) {
        PermMutexContext context = computeMutexContext(tenantId, passedEntries);

        // 检测到权限冲突时触发异步通知
        if (!context.conflictingOpIds().isEmpty()) {
            notifyPermConflict(tenantId, context.conflictingOpIds(), context.rules());
        }
        return passedEntries.stream()
            .filter(entry -> !context.isConflicting(entry))
            .collect(Collectors.toList());
    }

    @Override
    public BatchPermMutexEvaluator openBatchMutexEvaluator(Long tenantId) {
        return new BatchPermMutexEvaluatorImpl(tenantId);
    }

    /**
     * 请求级批量互斥评估器（T-PERM-061：静态数据共享装载 + 计算通知解耦）。
     * <p>
     * 规则惰性装载一次、操作索引按 distinct 类型惰性扩；剔除语义与
     * {@link #filterPermMutex} 逐分支一致（复用 {@link PermMutexContext} 的
     * 精确查表与两端同场判定），但不通知——通知由调用方 ledger 聚合后
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
     * 计算权限互斥上下文：操作索引、条目覆盖的操作ID集合、命中的互斥规则与冲突操作ID集合
     */
    private PermMutexContext computeMutexContext(Long tenantId, List<RolePermEntry> passedEntries) {
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(
            tenantId, ConflictType.PERM_MUTEX.getValue());

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

        Set<Long> conflictingOpIds = new HashSet<>();
        for (PermissionConflictRule rule : rules) {
            if (rule.getFirstOperationPermissionId() != null && rule.getSecondOperationPermissionId() != null) {
                if (opIds.contains(rule.getFirstOperationPermissionId()) && opIds.contains(rule.getSecondOperationPermissionId())) {
                    conflictingOpIds.add(rule.getFirstOperationPermissionId());
                    conflictingOpIds.add(rule.getSecondOperationPermissionId());
                }
            }
        }
        return new PermMutexContext(rules, opByTypeAndBit, opIds, conflictingOpIds);
    }

    /**
     * 权限互斥上下文（规则 + 操作索引 + 冲突操作ID集合）
     */
    private record PermMutexContext(
        List<PermissionConflictRule> rules,
        Map<String, OperationPermission> opByTypeAndBit,
        Set<Long> opIds,
        Set<Long> conflictingOpIds
    ) {
        OperationPermission grantedOperation(RolePermEntry entry) {
            return OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                opByTypeAndBit, entry.resourceType(), entry.grantedBits());
        }

        boolean isConflicting(RolePermEntry entry) {
            OperationPermission granted = grantedOperation(entry);
            return granted != null && conflictingOpIds.contains(granted.getId());
        }

    }

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
     * 本方法由同类方法内调用（self-invocation），无 Spring 代理，故不再标注
     * {@code @Async}（标注了也不生效）；"异步"职责统一归到底层
     * {@link AuditDomainService#asyncRecordLog}（其自身 {@code @Async} + REQUIRES_NEW
     * 在有界线程池异步写入）。本方法体仅同步组装日志条目后提交，不阻塞主流程。
     * </p>
     *
     * @param tenantId         租户ID
     * @param conflictingOpIds 冲突的操作权限ID集合
     * @param triggeredRules   触发的冲突规则列表
     */
    void notifyPermConflict(Long tenantId, Set<Long> conflictingOpIds, List<PermissionConflictRule> triggeredRules) {
        try {
            String detail = triggeredRules.stream()
                .filter(r -> conflictingOpIds.contains(r.getFirstOperationPermissionId())
                          || conflictingOpIds.contains(r.getSecondOperationPermissionId()))
                .map(r -> String.format("rule[%d]: op%d vs op%d", r.getId(),
                    r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId()))
                .collect(Collectors.joining("; "));
            log.warn("Permission conflict detected: tenantId={}, conflictingOps={}, rules={}",
                tenantId, conflictingOpIds, detail);
            auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
                tenantId, "PERMISSION", "CONFLICT_DETECTED", "permission_conflict_rule", null,
                String.format("Perm conflict blocked: tenantId=%d, ops=%s", tenantId, conflictingOpIds),
                null, null, null, OperatorContext.getRequestId(), null, null, null
            ));
        } catch (Exception e) {
            log.error("Failed to record permission conflict notification: tenantId={}", tenantId, e);
        }
    }
}