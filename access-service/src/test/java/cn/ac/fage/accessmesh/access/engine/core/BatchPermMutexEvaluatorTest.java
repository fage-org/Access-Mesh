package cn.ac.fage.accessmesh.access.engine.core;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConflictDomainServiceImpl;

/**
 * {@link BatchPermMutexEvaluator}（T-PERM-061 请求级批量互斥评估器）单元测试。
 * <p>
 * 锁计算零通知（互斥命中审计由新引擎按 ConflictEvidence 受控提交，T-PERM-088）、
 * 静态数据共享装载（规则一次、操作索引按 distinct 类型一次）与集合语义
 * （两端同场才冲突且两端全丢）。
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
    @Mock private OperationPermissionDomainService operationPermissionMapper;
    @Mock private cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService subjectDomainService;

    private PermissionConflictDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConflictDomainServiceImpl(conflictRuleMapper, cacheService,
            new ObjectMapper(), auditDomainService, operationPermissionMapper,
            subjectDomainService);
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
        lenient().when(operationPermissionMapper.selectByTenantAndResourceTypes(TENANT, java.util.Set.of(TYPE)))
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
        verify(operationPermissionMapper, times(1)).selectByTenantAndResourceTypes(TENANT, java.util.Set.of(TYPE));
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

    /**
     * I01 空规则短路（T-PERM-095）：规则装载为空时 compute 直接返回原条目，操作目录
     * 零装载——getDenied* 切换到批量评估器通道（PQ-01 逐 item 修复）后，无互斥规则
     * 租户不得多付一次 selectByTenantAndResourceTypes（裸 DB 查询、无缓存）。
     */
    @Test
    void computeMustShortCircuitOnEmptyRulesWithoutOperationLoading() {
        when(conflictRuleMapper.selectByConflictType(eq(TENANT), eq("PERM_MUTEX")))
            .thenReturn(List.of());

        BatchPermMutexEvaluator evaluator = service.openBatchMutexEvaluator(TENANT);
        BatchPermMutexEvaluator.PermMutexComputation computation =
            evaluator.compute(List.of(entry(VIEW_BIT), entry(UPDATE_BIT)));

        assertEquals(2, computation.filtered().size(), "空规则：条目原样保留");
        assertTrue(computation.triggeredRuleIds().isEmpty());
        verify(operationPermissionMapper, never()).selectByTenantAndResourceTypes(any(), any());
    }
}
