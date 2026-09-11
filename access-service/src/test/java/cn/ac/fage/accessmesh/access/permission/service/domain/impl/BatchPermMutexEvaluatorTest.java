package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BatchPermMutexEvaluator}（T-PERM-061 请求级批量互斥评估器）单元测试。
 * <p>
 * 锁计算与通知解耦（compute 零通知——通知由 ledger flush 显式触发）、静态数据共享装载
 * （规则一次、操作索引按 distinct 类型一次）、集合语义（两端同场才冲突且两端全丢）与
 * notifyHits 聚合形态（每 (组, ruleId) 一条审计行、未触发规则不出现）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BatchPermMutexEvaluatorTest {

    private static final Long TENANT = 1L;
    private static final int TYPE = 4;
    private static final long VIEW_BIT = 2L;
    private static final long UPDATE_BIT = 4L;

    @Mock private PermissionConflictRuleMapper conflictRuleMapper;
    @Mock private CacheService cacheService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private OperationPermissionMapper operationPermissionMapper;

    private PermissionConflictDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConflictDomainServiceImpl(conflictRuleMapper, cacheService,
            new ObjectMapper(), auditDomainService, operationPermissionMapper);
    }

    private void stubRule(Long ruleId, Long firstOpId, Long secondOpId) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(ruleId);
        rule.setFirstOperationPermissionId(firstOpId);
        rule.setSecondOperationPermissionId(secondOpId);
        lenient().when(conflictRuleMapper.selectByConflictType(eq(TENANT), eq("PERM_MUTEX")))
            .thenReturn(List.of(rule));
    }

    private void stubOperations(long... bits) {
        List<OperationPermission> ops = new java.util.ArrayList<>();
        for (long bit : bits) {
            OperationPermission op = new OperationPermission();
            op.setId(bit * 100);
            op.setResourceType(TYPE);
            op.setCode("OP" + bit);
            op.setBinaryBit(bit);
            op.setInheritMask(0L);
            ops.add(op);
        }
        lenient().when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, TYPE))
            .thenReturn(ops);
    }

    private RolePermEntry entry(long grantedBits) {
        return new RolePermEntry(grantedBits * 1000, 20L, 200L, "sys:r", TYPE, grantedBits,
            "OP" + grantedBits, grantedBits, "MANUAL", false, null, false, null, false);
    }

    @Test
    void computeMustNotNotifyAndLoadRulesOncePerRequest() {
        stubRule(1L, 200L, 400L);
        stubOperations(VIEW_BIT, UPDATE_BIT);

        BatchPermMutexEvaluator evaluator = service.openBatchMutexEvaluator(TENANT);
        evaluator.compute(List.of(entry(VIEW_BIT), entry(UPDATE_BIT)));
        evaluator.compute(List.of(entry(VIEW_BIT)));

        // 计算不通知（解耦）；规则请求级装载一次
        verify(auditDomainService, never()).asyncRecordLog(any());
        verify(conflictRuleMapper, times(1)).selectByConflictType(eq(TENANT), eq("PERM_MUTEX"));
        // 操作索引按 distinct 类型装载一次
        verify(operationPermissionMapper, times(1)).selectByTenantAndResourceType(TENANT, TYPE);
    }

    @Test
    void computeMustFollowCollectionSemantics() {
        stubRule(1L, 200L, 400L);
        stubOperations(VIEW_BIT, UPDATE_BIT);

        BatchPermMutexEvaluator evaluator = service.openBatchMutexEvaluator(TENANT);

        // 两端同场（VIEW+UPDATE）→ 命中规则且两端全丢
        BatchPermMutexEvaluator.PermMutexComputation both = evaluator.compute(
            List.of(entry(VIEW_BIT), entry(UPDATE_BIT)));
        assertTrue(both.filtered().isEmpty(), "互斥两端同场：两端全丢");
        assertEquals(Set.of(1L), both.triggeredRuleIds());

        // 单端在场（仅 UPDATE）→ 不冲突、条目保留（条件-互斥顺序锁⑩的语义核：
        // 条件先摘 VIEW 一端后互斥不成立）
        BatchPermMutexEvaluator.PermMutexComputation single = evaluator.compute(
            List.of(entry(UPDATE_BIT)));
        assertEquals(1, single.filtered().size(), "互斥单端在场不构成冲突");
        assertTrue(single.triggeredRuleIds().isEmpty());
    }

    @Test
    void notifyHitsMustEmitOneAuditRowPerGroupRuleAndSkipUnknown() {
        stubRule(1L, 200L, 400L);

        BatchPermMutexEvaluator evaluator = service.openBatchMutexEvaluator(TENANT);
        evaluator.notifyHits(TENANT, List.of(
            new BatchPermMutexEvaluator.MutexHit("INSTANCE:MENU:VIEW:-:-:FLAT", 1L, 2),
            new BatchPermMutexEvaluator.MutexHit("INSTANCE:MENU:VIEW:-:-:FLAT", 99L, 1)));

        // 每 (组, ruleId) 一条审计行；未知 ruleId 跳过（不出现半条通知）
        ArgumentCaptor<AuditDomainService.OperationLogEntry> captor =
            ArgumentCaptor.forClass(AuditDomainService.OperationLogEntry.class);
        verify(auditDomainService, times(1)).asyncRecordLog(captor.capture());
        String summary = String.valueOf(captor.getValue().summary());
        assertTrue(summary.contains("group=INSTANCE:MENU:VIEW:-:-:FLAT"), "summary 含组标识: " + summary);
        assertTrue(summary.contains("hitItemCount=2"), "summary 含命中 item 数: " + summary);
        assertTrue(summary.contains("rule[1]"), "summary 含实际命中规则 detail: " + summary);
    }

    @Test
    void notifyHitsWithEmptyLedgerMustBeNoOp() {
        BatchPermMutexEvaluator evaluator = service.openBatchMutexEvaluator(TENANT);
        evaluator.notifyHits(TENANT, List.of());
        verify(auditDomainService, never()).asyncRecordLog(any());
    }
}
