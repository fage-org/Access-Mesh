package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.engine.query.ConflictEvidence.PermRuleId;
import cn.ac.fage.accessmesh.access.engine.query.ConflictEvidence.RolePair;
import cn.ac.fage.accessmesh.access.engine.query.ConflictEvidence.RuleRef;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 根级受控证据提交器（T-PERM-088，设计 §6.1 / §4.1 QueryAuditCollector）。
 * <p>
 * 一次 execute 在根级至多提交一次（幂等闸），纯规则计算与父项自身不发日志：
 * 主体解析角色对、根项阶段命中、共享父阶段命中统一在提交期从 {@link RunState}
 * 聚合为 {@link ConflictEvidence}（父证据一条关联全部受影响根项）。保持既有非阻塞
 * 提交策略——每条证据一行 CONFLICT_DETECTED（{@code asyncRecordLog} 异步落库，
 * summary 由审计服务统一 512 截断兜底，单行单规则不落旧单条路径的多规则拼接形态）；
 * 提交失败只记技术日志与指标，不覆盖主查询异常，不宣称持久必达或跨请求 exactly-once。
 * 角色对证据沿旧口径按「租户×用户×命中对」每 JVM 1 小时去重（T-PERM-063 防快照链路
 * 刷屏；提交期同步失败回滚去重标记允许窗口内重试）；PERM 规则证据不去重（旧引擎
 * 批量 (组,ruleId) 轨亦无跨请求去重）——两类口径 2026-09-26 用户拍板沿旧。
 * </p>
 */
final class QueryAuditCollector {

    private static final Logger log = LoggerFactory.getLogger(QueryAuditCollector.class);
    private static final String SUBJECT_ITEM_ID = "subject";

    private final AuditDomainService audit;
    private final QueryEngineMetrics metrics;
    // 沿旧表双保险口径：有界（10000 条）＋ expireAfterWrite(1h)（外评 P3：只沿 TTL 丢了界）
    private final Cache<String, Boolean> rolePairNotified = Caffeine.newBuilder()
        .maximumSize(10_000).expireAfterWrite(Duration.ofHours(1)).build();

    QueryAuditCollector(AuditDomainService audit, QueryEngineMetrics metrics) {
        this.audit = audit;
        this.metrics = metrics == null ? QueryEngineMetrics.noop() : metrics;
    }

    /** 根 execute finally 唯一提交口（先于 RunState 释放）；任何提交期异常都不外抛。 */
    void submitConfirmedEvidenceOnce(RunState run) {
        if (!run.markEvidenceSubmitted()) {
            return;
        }
        List<ConflictEvidence> evidence;
        try {
            evidence = collect(run);
        } catch (RuntimeException collectionError) {
            // 聚合期缺陷不允许从 finally 外抛覆盖主查询异常（§6.1 不覆盖主异常）
            log.error("Query evidence collection failed: executionId={}", run.executionId(), collectionError);
            return;
        }
        if (evidence.isEmpty()) {
            return;
        }
        Long userId = run.request().subject() instanceof User user ? user.userId() : null;
        for (ConflictEvidence item : evidence) {
            if (item.ruleRef() instanceof RolePair pair && userId != null) {
                String dedupKey = run.request().tenantId() + ":" + userId
                    + ":" + pair.firstRoleId() + ":" + pair.secondRoleId();
                if (rolePairNotified.getIfPresent(dedupKey) != null) {
                    continue;
                }
                rolePairNotified.put(dedupKey, Boolean.TRUE);
                try {
                    submit(run, item);
                } catch (RuntimeException submitError) {
                    rolePairNotified.invalidate(dedupKey);
                    recordFailure(item, run, submitError);
                }
                continue;
            }
            try {
                submit(run, item);
            } catch (RuntimeException submitError) {
                recordFailure(item, run, submitError);
            }
        }
    }

    private void submit(RunState run, ConflictEvidence evidence) {
        audit.asyncRecordLog(new AuditDomainService.OperationLogEntry(
            run.request().tenantId(), "PERMISSION", "CONFLICT_DETECTED", "permission_conflict_rule", null,
            summary(evidence), null, null, null, null, null, null, null));
    }

    /** 单行单规则的结构化摘要（角色/授权 ID 仅入内部审计行）。固定字段全部前移、变长 affected
     * 殿后：512 尾截时只损失 key 列表尾部，completion 等关键标记不先丢（外评 P3 合并修）；
     * key 列表不按条数折叠（外评 P3：9 个短 key 远短于列上限，折叠先丢关联信息）。 */
    private static String summary(ConflictEvidence evidence) {
        RuleRef rule = evidence.ruleRef();
        if (rule instanceof RolePair pair) {
            return String.format("Role mutex evidence: execution=%s, pair=%d vs %d, completion=%s, affected=%s",
                evidence.executionId(), pair.firstRoleId(), pair.secondRoleId(),
                evidence.completion(), evidence.affectedRootItemKeys());
        }
        PermRuleId perm = (PermRuleId) rule;
        return String.format("Perm conflict evidence: execution=%s, item=%s, stage=%s, rule=%d, ops=%s, completion=%s, affected=%s",
            evidence.executionId(), evidence.evaluationItemId(), evidence.stage(), perm.ruleId(),
            evidence.actualConflictingOperationIds(), evidence.completion(), evidence.affectedRootItemKeys());
    }

    private void recordFailure(ConflictEvidence evidence, RunState run, RuntimeException error) {
        metrics.evidenceSubmissionFailed(evidence.ruleRef() instanceof RolePair
            ? QueryEngineMetrics.EvidenceKind.ROLE_PAIR : QueryEngineMetrics.EvidenceKind.PERM_RULE);
        log.error("Query evidence submission failed: executionId={}, rule={}",
            run.executionId(), evidence.ruleRef(), error);
    }

    /** 按 execution＋内部 item＋stage＋ruleRef 聚合（主体解析证据 stage=null、item=subject）。 */
    private static List<ConflictEvidence> collect(RunState run) {
        ConflictEvidence.Completion completion = run.executionFailed()
            ? ConflictEvidence.Completion.EXECUTION_ERROR_AFTER_CONFIRMED_STAGE
            : ConflictEvidence.Completion.COMPLETE;
        List<ConflictEvidence> evidence = new ArrayList<>();
        List<String> rootKeys = run.request().items().stream().map(QueryItem::key).toList();
        run.roleHits().forEach(pair -> evidence.add(new ConflictEvidence(run.executionId(),
            new RolePair(pair.firstRoleId(), pair.secondRoleId()), SUBJECT_ITEM_ID,
            rootKeys, null, Set.of(), completion)));
        for (var entry : run.items().entrySet()) {
            entry.getValue().mutexHits.forEach((stage, hits) ->
                hits.forEach(hit -> evidence.add(permEvidence(run, hit, entry.getKey().key(),
                    List.of(entry.getKey().key()), stage, completion))));
        }
        for (var parent : run.parents().values()) {
            parent.execution.mutexHits.forEach((stage, hits) ->
                hits.forEach(hit -> evidence.add(permEvidence(run, hit, parent.evidenceItemId,
                    List.copyOf(new LinkedHashSet<>(parent.affectedItemKeys)), stage, completion))));
        }
        return evidence;
    }

    private static ConflictEvidence permEvidence(RunState run, BatchPermMutexEvaluator.MutexRuleRef hit,
                                                 String evaluationItemId, List<String> affectedRootItemKeys,
                                                 Stage stage, ConflictEvidence.Completion completion) {
        // LinkedHashSet 保两端插入序：审计摘要 ops=[first, second] 确定可断言
        Set<Long> ops = new java.util.LinkedHashSet<>(List.of(hit.firstOperationPermissionId(), hit.secondOperationPermissionId()));
        return new ConflictEvidence(run.executionId(), new PermRuleId(hit.ruleId()), evaluationItemId,
            affectedRootItemKeys, stage, java.util.Collections.unmodifiableSet(ops), completion);
    }
}
