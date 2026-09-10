package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.permission.enums.ConflictType;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionConflictDomainServiceImpl} 权限互斥过滤测试（保留面 filterPermMutex 运行时路径）。
 * <p>
 * 原 filterPermMutexWithDrops 明细用例族已随 explain 端点删除（T-PERM-059，2026-09-10）；
 * 本文件重写为 filterPermMutex（引擎运行时过滤）驱动的行为锁：规则命中两侧同丢、
 * 单侧在场不生效、无规则全保留、冲突触发异步通知。
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
        OperationPermission operation = new OperationPermission();
        operation.setId(id);
        operation.setResourceType(resourceType);
        operation.setBinaryBit(bit);
        operation.setCode(code);
        return operation;
    }

    private RolePermEntry entry(Long permissionId, Long roleId, long grantedBits) {
        return new RolePermEntry(permissionId, roleId, 200L, "sys:user", 1, grantedBits,
            "VIEW", grantedBits, "DIRECT", true, null, false, null, false);
    }

    private PermissionConflictRule rule(Long id, Long firstOpId, Long secondOpId) {
        PermissionConflictRule conflictRule = new PermissionConflictRule();
        conflictRule.setId(id);
        conflictRule.setConflictType(ConflictType.PERM_MUTEX.getValue());
        conflictRule.setFirstOperationPermissionId(firstOpId);
        conflictRule.setSecondOperationPermissionId(secondOpId);
        return conflictRule;
    }

    /** 互斥规则命中：两侧条目都被丢弃、无关条目保留、触发异步冲突通知 */
    @Test
    void shouldDropBothSidesAndNotifyWhenRuleFires() {
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of(rule(9L, 11L, 12L)));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(
                op(11L, 1, 1L, "VIEW"),
                op(12L, 1, 2L, "MANAGE"),
                op(13L, 1, 4L, "SYNC")));

        // 条目 A 授 VIEW(bit=1)、条目 B 授 MANAGE(bit=2) —— 规则两侧同时命中 → 双双丢弃；
        // 条目 C 授 SYNC(bit=4) —— 不参与冲突 → 保留
        List<RolePermEntry> survivors = service.filterPermMutex(TENANT, List.of(
            entry(501L, 20L, 1L),
            entry(502L, 21L, 2L),
            entry(503L, 22L, 4L)));

        assertEquals(1, survivors.size(), "互斥两侧条目须全部丢弃");
        assertEquals(503L, survivors.get(0).permissionId());
        // 运行时路径检测到冲突须触发异步通知（区别于已删的只读排查路径）
        verify(auditDomainService).asyncRecordLog(org.mockito.ArgumentMatchers.any(AuditDomainService.OperationLogEntry.class));
    }

    /** 无命中规则：全部保留、不触发通知 */
    @Test
    void shouldKeepAllEntriesWhenNoRuleFires() {
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of());
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(op(11L, 1, 1L, "VIEW")));

        List<RolePermEntry> survivors = service.filterPermMutex(TENANT, List.of(entry(501L, 20L, 1L)));

        assertEquals(1, survivors.size());
        verifyNoInteractions(auditDomainService);
    }

    /** 单侧命中（另一侧操作无条目覆盖）：规则不生效，条目保留 */
    @Test
    void shouldKeepEntriesWhenOnlyOneSidePresent() {
        when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.PERM_MUTEX.getValue()))
            .thenReturn(List.of(rule(9L, 11L, 12L)));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, 1))
            .thenReturn(List.of(
                op(11L, 1, 1L, "VIEW"),
                op(12L, 1, 2L, "MANAGE")));

        // 仅 VIEW 在场（无 MANAGE 条目）→ 冲突不成立 → 保留
        List<RolePermEntry> survivors = service.filterPermMutex(TENANT, List.of(entry(501L, 20L, 1L)));

        assertEquals(1, survivors.size());
        verifyNoInteractions(auditDomainService);
    }
}
