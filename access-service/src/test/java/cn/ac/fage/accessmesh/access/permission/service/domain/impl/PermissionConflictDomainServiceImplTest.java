package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.permission.enums.ConflictType;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.vo.MutexFilterResult;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionConflictDomainServiceImpl} 权限互斥过滤测试（T-PERM-033 filterPermMutexWithDrops）。
 * <p>
 * 锁定：丢弃明细（被丢弃条目 + 命中规则ID + 两侧操作码）、幸存条目保留、
 * 只读排查路径不触发冲突通知、与 filterPermMutex 过滤语义一致。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionConflictDomainServiceImplTest {

    private static final Long TENANT = 1L;

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

    private OperationPermission op(Long id, Integer resourceType, long bit, String code) {
        OperationPermission op = new OperationPermission();
        op.setId(id);
        op.setResourceType(resourceType);
        op.setBinaryBit(bit);
        op.setCode(code);
        return op;
    }

    private RolePermEntry entry(Long permissionId, Long roleId, long grantedBits) {
        return new RolePermEntry(permissionId, roleId, 200L, "sys:user", 1, grantedBits,
            "VIEW", grantedBits, "DIRECT", true, null, false, null, false);
    }

    /** 互斥规则命中：两侧条目都被丢弃并携带规则详情，无关条目保留，不触发通知 */
    @Test
    void shouldExposeDropsWithRuleDetailWithoutNotify() {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(9L);
        rule.setConflictType(ConflictType.PERM_MUTEX.getValue());
        rule.setFirstOperationPermissionId(11L);
        rule.setSecondOperationPermissionId(12L);
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of(rule));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(
                op(11L, 1, 1L, "VIEW"),
                op(12L, 1, 2L, "MANAGE"),
                op(13L, 1, 4L, "SYNC")));

        // 条目 A 授予 VIEW(bit=1)、条目 B 授予 MANAGE(bit=2) —— 与规则两侧同时命中 → 双双丢弃；
        // 条目 C 授予 SYNC(bit=4) —— 不参与冲突 → 保留
        MutexFilterResult result = service.filterPermMutexWithDrops(TENANT, List.of(
            entry(501L, 20L, 1L),
            entry(502L, 21L, 2L),
            entry(503L, 22L, 4L)));

        assertEquals(1, result.survivors().size());
        assertEquals(503L, result.survivors().get(0).permissionId());
        assertEquals(2, result.drops().size());
        MutexFilterResult.MutexDrop first = result.drops().get(0);
        assertEquals(501L, first.entry().permissionId());
        assertEquals(9L, first.ruleId());
        assertEquals("VIEW", first.firstOperationCode());
        assertEquals("MANAGE", first.secondOperationCode());
        // 只读排查路径不写冲突通知日志
        verifyNoInteractions(auditDomainService);
    }

    /** 无命中规则：全部保留、无丢弃 */
    @Test
    void shouldKeepAllEntriesWhenNoRuleFires() {
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of());
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(op(11L, 1, 1L, "VIEW")));

        MutexFilterResult result = service.filterPermMutexWithDrops(TENANT, List.of(entry(501L, 20L, 1L)));

        assertEquals(1, result.survivors().size());
        assertTrue(result.drops().isEmpty());
    }

    /** 单侧命中（另一侧操作无条目覆盖）：规则不生效，条目保留（与 filterPermMutex 语义一致） */
    @Test
    void shouldKeepEntriesWhenOnlyOneSidePresent() {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(9L);
        rule.setConflictType(ConflictType.PERM_MUTEX.getValue());
        rule.setFirstOperationPermissionId(11L);
        rule.setSecondOperationPermissionId(12L);
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of(rule));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(op(11L, 1, 1L, "VIEW"), op(12L, 1, 2L, "MANAGE")));

        MutexFilterResult result = service.filterPermMutexWithDrops(TENANT, List.of(entry(501L, 20L, 1L)));

        assertEquals(1, result.survivors().size());
        assertTrue(result.drops().isEmpty());
    }

    /** 与 filterPermMutex 语义一致：丢弃集合相同（同一上下文计算复用） */
    @Test
    void shouldAlignSurvivorsWithFilterPermMutex() {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(9L);
        rule.setConflictType(ConflictType.PERM_MUTEX.getValue());
        rule.setFirstOperationPermissionId(11L);
        rule.setSecondOperationPermissionId(12L);
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of(rule));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(op(11L, 1, 1L, "VIEW"), op(12L, 1, 2L, "MANAGE")));

        List<RolePermEntry> input = List.of(entry(501L, 20L, 1L), entry(502L, 21L, 2L), entry(503L, 22L, 4L));
        List<RolePermEntry> filtered = service.filterPermMutex(TENANT, input);
        MutexFilterResult detailed = service.filterPermMutexWithDrops(TENANT, input);

        assertEquals(filtered, detailed.survivors());
    }
}
