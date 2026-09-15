package cn.ac.fage.accessmesh.access.rule.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.rule.enums.ConflictType;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionConflictDomainServiceImpl} 互斥域测试。
 * <p>
 * filterPermMutex（引擎运行时过滤）：规则命中两侧同丢、单侧在场不生效、无规则全保留、
 * 冲突触发异步通知（原明细用例族已随 explain 端点删除，T-PERM-059）。
 * T-PERM-063 补：filterRoleMutex 双删 + CONFLICT_DETECTED 日志去重限流（旧实现双删
 * 静默无痕，日志断言在旧实现下必红）、授予前冲突检测（DB 直查）、存量双持查询
 * （有效角色集收敛）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionConflictDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionConflictRuleMapper conflictRuleMapper;
    @Mock private CacheService cacheService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private OperationPermissionDomainService operationPermissionMapper;
    @Mock private SubjectDomainService subjectDomainService;

    private PermissionConflictDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConflictDomainServiceImpl(conflictRuleMapper, cacheService,
            new ObjectMapper(), auditDomainService, operationPermissionMapper,
            subjectDomainService);
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
        when(operationPermissionMapper.selectByTenantAndResourceTypes(TENANT, java.util.Set.of(1)))
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
        when(operationPermissionMapper.selectByTenantAndResourceTypes(TENANT, java.util.Set.of(1)))
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
        when(operationPermissionMapper.selectByTenantAndResourceTypes(TENANT, java.util.Set.of(1)))
            .thenReturn(List.of(
                op(11L, 1, 1L, "VIEW"),
                op(12L, 1, 2L, "MANAGE")));

        // 仅 VIEW 在场（无 MANAGE 条目）→ 冲突不成立 → 保留
        List<RolePermEntry> survivors = service.filterPermMutex(TENANT, List.of(entry(501L, 20L, 1L)));

        assertEquals(1, survivors.size());
        verifyNoInteractions(auditDomainService);
    }

    @Nested
    class RoleMutexFilterAndLogging {

        /** 双删命中记日志且去重：同用户同规则对窗口内重复快照不重复记（旧实现无日志，必红） */
        @Test
        void shouldDropBothRolesAndLogOncePerUserRuleWithinDedupWindow() {
            when(cacheService.get(AccessCacheCatalog.ROLE_MUTEX_RULE, TENANT, "all"))
                .thenReturn("[{\"first\":100,\"second\":200}]");

            Set<Long> first = service.filterRoleMutex(TENANT, 20L, Set.of(100L, 200L, 300L));
            assertEquals(Set.of(300L), first);

            Set<Long> second = service.filterRoleMutex(TENANT, 20L, Set.of(100L, 200L, 300L));
            assertEquals(Set.of(300L), second);

            verify(auditDomainService, times(1)).asyncRecordLog(any(AuditDomainService.OperationLogEntry.class));
        }

        /** 不同用户/不同规则对各自记日志 */
        @Test
        void shouldLogAgainForDifferentUserOrPair() {
            when(cacheService.get(AccessCacheCatalog.ROLE_MUTEX_RULE, TENANT, "all"))
                .thenReturn("[{\"first\":100,\"second\":200},{\"first\":300,\"second\":400}]");

            service.filterRoleMutex(TENANT, 20L, Set.of(100L, 200L));
            service.filterRoleMutex(TENANT, 21L, Set.of(100L, 200L));
            service.filterRoleMutex(TENANT, 20L, Set.of(300L, 400L));

            verify(auditDomainService, times(3)).asyncRecordLog(any(AuditDomainService.OperationLogEntry.class));
        }

        /** 单侧在场不构成互斥：保留且不记日志 */
        @Test
        void shouldNotLogWhenNoPairBothPresent() {
            when(cacheService.get(AccessCacheCatalog.ROLE_MUTEX_RULE, TENANT, "all"))
                .thenReturn("[{\"first\":100,\"second\":200}]");

            Set<Long> result = service.filterRoleMutex(TENANT, 20L, Set.of(100L, 300L));

            assertEquals(Set.of(100L, 300L), result);
            verifyNoInteractions(auditDomainService);
        }
    }

    @Nested
    class AssignMutexConflictDetection {

        /** 写路径校验走 DB 直查（不经缓存），授予后集合双端在场命中（同批双端由集合语义覆盖） */
        @Test
        void shouldFindAssignConflictsFromFreshDbRules() {
            PermissionConflictRule roleRule = new PermissionConflictRule();
            roleRule.setId(9L);
            roleRule.setConflictType(ConflictType.ROLE_MUTEX.getValue());
            roleRule.setFirstAbstractRoleId(100L);
            roleRule.setSecondAbstractRoleId(200L);
            when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.ROLE_MUTEX.getValue()))
                .thenReturn(List.of(roleRule));

            List<PermissionConflictDomainService.RoleMutexAssignConflict> conflicts =
                service.findAssignMutexConflicts(TENANT, Map.of(
                    20L, Set.of(100L, 200L),
                    21L, Set.of(100L),
                    22L, Set.of(500L)));

            assertEquals(1, conflicts.size());
            assertEquals(20L, conflicts.get(0).userId());
            assertEquals(9L, conflicts.get(0).ruleId());
            verify(cacheService, never()).get(any(), any(), any());
        }

        /** 空入参/无规则零成本短路 */
        @Test
        void shouldShortCircuitWhenNoInputOrNoRules() {
            assertEquals(List.of(), service.findAssignMutexConflicts(TENANT, Map.of()));
            when(conflictRuleMapper.selectByConflictType(TENANT, ConflictType.ROLE_MUTEX.getValue()))
                .thenReturn(List.of());
            assertEquals(List.of(), service.findAssignMutexConflicts(TENANT, Map.of(20L, Set.of(100L, 200L))));
        }
    }

    @Nested
    class UsersHoldingBothRoles {

        /** 候选反查 + 有效角色集 AND 收敛：一端禁用/过期的用户不计存量持有 */
        @Test
        void shouldFindHoldersByEffectiveSet() {
            when(subjectDomainService.findUserIdsByEffectiveRoles(TENANT, Set.of(100L, 200L)))
                .thenReturn(Set.of(20L, 21L));
            when(subjectDomainService.batchResolveEffectiveRoles(TENANT, Set.of(20L, 21L)))
                .thenReturn(Map.of(20L, Set.of(100L, 200L), 21L, Set.of(100L)));

            List<Long> holders = service.findUsersHoldingBothRoles(TENANT, 100L, 200L);

            assertEquals(List.of(20L), holders);
        }

        /** 组角色间接持有入候选（评审 P1-1）：经组展开持有对端同样计双持——
         *  直授行候选的旧实现在此用例下漏判必红 */
        @Test
        void shouldFindHolderViaGroupRoleExpansion() {
            // 用户 30 无 (100,200) 直授行：100 经 GROUP_ROLE 展开获得、200 直授——
            // 候选必须来自按角色反查（含组路径）才会包含 30
            when(subjectDomainService.findUserIdsByEffectiveRoles(TENANT, Set.of(100L, 200L)))
                .thenReturn(Set.of(30L));
            when(subjectDomainService.batchResolveEffectiveRoles(TENANT, Set.of(30L)))
                .thenReturn(Map.of(30L, Set.of(100L, 200L)));

            assertEquals(List.of(30L), service.findUsersHoldingBothRoles(TENANT, 100L, 200L));
        }

        /** 候选为空：短路不触有效角色解析 */
        @Test
        void shouldReturnEmptyWhenNoCandidates() {
            when(subjectDomainService.findUserIdsByEffectiveRoles(TENANT, Set.of(100L, 200L)))
                .thenReturn(Set.of());

            assertEquals(List.of(), service.findUsersHoldingBothRoles(TENANT, 100L, 200L));
            verify(subjectDomainService, never()).batchResolveEffectiveRoles(anyLong(), any());
        }
    }
}
