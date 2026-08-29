package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.permission.enums.ConflictType;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.vo.MutexFilterResult;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final OperationPermissionMapper operationPermissionMapper;

    /**
     * 构造函数注入依赖
     *
     * @param conflictRuleMapper        权限冲突规则数据访问层
     * @param cacheService              统一缓存服务，用于缓存角色互斥规则
     * @param objectMapper              JSON解析器
     * @param auditDomainService        审计领域服务，用于记录冲突通知
     * @param operationPermissionMapper 操作权限数据访问层，用于查找冲突操作权限
     */
    public PermissionConflictDomainServiceImpl(PermissionConflictRuleMapper conflictRuleMapper,
                                                CacheService cacheService,
                                                ObjectMapper objectMapper,
                                                AuditDomainService auditDomainService,
                                                OperationPermissionMapper operationPermissionMapper) {
        this.conflictRuleMapper = conflictRuleMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.auditDomainService = auditDomainService;
        this.operationPermissionMapper = operationPermissionMapper;
    }

    /**
     * 过滤角色互斥冲突
     * <p>
     * 根据角色互斥规则过滤有效角色集合。
     * 如果用户同时拥有互斥的两个角色，则同时移除这两个角色。
     * 角色互斥规则通过 CacheService 缓存（JSON格式）。
     * </p>
     *
     * @param tenantId        租户ID
     * @param effectiveRoleIds 有效角色ID集合
     * @return 过滤后的有效角色ID集合（移除互斥角色）
     */
    @Override
    public Set<Long> filterRoleMutex(Long tenantId, Set<Long> effectiveRoleIds) {
        // 从缓存获取角色互斥规则（JSON格式）
        String cachedJson = cacheService.get(PermCacheCatalog.ROLE_MUTEX_RULE, tenantId, "all");

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
            if (pair.first != null && pair.second != null
                && result.contains(pair.first) && result.contains(pair.second)) {
                result.remove(pair.first);
                result.remove(pair.second);
            }
        }
        return result;
    }

    /**
     * 从数据库加载角色互斥规则并缓存
     * <p>
     * T-ACCESS-008：DB 读取前记录读取起点，回填只写剩余 TTL。
     * </p>
     */
    private List<RoleMutexPair> loadMutexRulesFromDb(Long tenantId) {
        CacheReadToken<String> readToken = cacheService.beginRead(PermCacheCatalog.ROLE_MUTEX_RULE);
        List<PermissionConflictRule> rules = conflictRuleMapper.selectByConflictType(
            tenantId, ConflictType.ROLE_MUTEX.getValue());
        List<RoleMutexPair> mutexPairs = rules.stream()
            .map(r -> new RoleMutexPair(r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId()))
            .collect(Collectors.toList());

        // 回填缓存（JSON格式，剩余 TTL）
        if (!mutexPairs.isEmpty()) {
            try {
                List<Map<String, Long>> toCache = mutexPairs.stream()
                    .map(p -> Map.of("first", p.first, "second", p.second))
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

    /**
     * 权限互斥过滤（带丢弃明细，T-PERM-033 explain DTO 扩展）。
     * <p>
     * 过滤语义与 {@link #filterPermMutex} 一致（共用互斥上下文计算），
     * 但不触发冲突通知，并保留被丢弃条目及其命中规则。
     * </p>
     */
    @Override
    public MutexFilterResult filterPermMutexWithDrops(Long tenantId, List<RolePermEntry> passedEntries) {
        PermMutexContext context = computeMutexContext(tenantId, passedEntries);

        List<RolePermEntry> survivors = new ArrayList<>();
        List<MutexFilterResult.MutexDrop> drops = new ArrayList<>();
        for (RolePermEntry entry : passedEntries) {
            OperationPermission granted = context.grantedOperation(entry);
            if (granted == null || !context.conflictingOpIds().contains(granted.getId())) {
                survivors.add(entry);
                continue;
            }
            PermissionConflictRule firedRule = context.firedRule(granted.getId());
            drops.add(new MutexFilterResult.MutexDrop(
                entry,
                firedRule != null ? firedRule.getId() : null,
                firedRule != null ? context.codeOf(firedRule.getFirstOperationPermissionId()) : null,
                firedRule != null ? context.codeOf(firedRule.getSecondOperationPermissionId()) : null));
        }
        return new MutexFilterResult(survivors, drops);
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

        List<OperationPermission> allOps = resourceTypes.stream()
            .flatMap(resourceType -> operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType).stream())
            .toList();
        Map<String, OperationPermission> opByTypeAndBit = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(allOps);
        Map<Long, OperationPermission> opById = allOps.stream()
            .collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));

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
        return new PermMutexContext(rules, opByTypeAndBit, opById, opIds, conflictingOpIds);
    }

    /**
     * 权限互斥上下文（规则 + 操作索引 + 冲突操作ID集合）
     */
    private record PermMutexContext(
        List<PermissionConflictRule> rules,
        Map<String, OperationPermission> opByTypeAndBit,
        Map<Long, OperationPermission> opById,
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

        /** 命中含指定冲突操作的第一条互斥规则（找不到返回 null） */
        PermissionConflictRule firedRule(Long conflictingOpId) {
            return rules.stream()
                .filter(r -> conflictingOpId.equals(r.getFirstOperationPermissionId())
                    || conflictingOpId.equals(r.getSecondOperationPermissionId()))
                .findFirst().orElse(null);
        }

        /** 操作权限ID → 操作码（未解析返回 null） */
        String codeOf(Long operationPermissionId) {
            OperationPermission op = operationPermissionId == null ? null : opById.get(operationPermissionId);
            return op != null ? op.getCode() : null;
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
                null, null, null, null, null, null, null
            ));
        } catch (Exception e) {
            log.error("Failed to record permission conflict notification: tenantId={}", tenantId, e);
        }
    }
}