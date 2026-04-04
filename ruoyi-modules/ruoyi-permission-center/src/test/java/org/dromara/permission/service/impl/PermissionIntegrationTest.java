package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.*;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.mapper.*;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.support.PermissionTreePathManager;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("dev")
class PermissionIntegrationTest {

    private static final Long NOT_DELETED = PermissionConstants.NOT_DELETED;

    @Mock private PcAbstractUserMapper abstractUserMapper;
    @Mock private PcAbstractRoleMapper abstractRoleMapper;
    @Mock private PcResourceEntityMapper resourceEntityMapper;
    @Mock private PcOperationPermissionMapper operationPermissionMapper;
    @Mock private PcPermissionConditionMapper permissionConditionMapper;
    @Mock private PcUserRoleMapper userRoleMapper;
    @Mock private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock private PcPermissionChangeLogMapper changeLogMapper;
    @Mock private PcResourceDependencyMapper resourceDependencyMapper;
    @Mock private PcPermissionConflictRuleMapper conflictRuleMapper;
    @Mock private PermissionTreePathManager treePathManager;
    @Mock private TypeDefinitionReader typeDefinitionReader;
    @Mock private PermissionService permissionService;

    private PermissionChangeLogServiceImpl changeLogService;
    private PermissionSyncServiceImpl syncService;
    private PermissionCheckServiceImpl checkService;
    private UserRoleServiceImpl userRoleService;
    private ConflictRuleServiceImpl conflictRuleService;

    private final AtomicLong idSeq = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        idSeq.set(1000);
        changeLogService = new PermissionChangeLogServiceImpl(changeLogMapper);
        lenient().doAnswer(inv -> {
            String parentPath = inv.getArgument(0);
            Long id = inv.getArgument(1);
            return parentPath == null ? "/" + id : parentPath + "/" + id;
        }).when(treePathManager).buildPath(any(), any());
        syncService = new PermissionSyncServiceImpl(
            abstractUserMapper, abstractRoleMapper, resourceEntityMapper,
            operationPermissionMapper, permissionConditionMapper,
            userRoleMapper, roleResourcePermissionMapper, changeLogService,
            treePathManager, typeDefinitionReader
        );
        checkService = new PermissionCheckServiceImpl(
            userRoleMapper, abstractRoleMapper, roleResourcePermissionMapper,
            permissionConditionMapper, resourceDependencyMapper
        );
        userRoleService = new UserRoleServiceImpl(
            userRoleMapper, abstractUserMapper, abstractRoleMapper, changeLogService, permissionService
        );
        conflictRuleService = new ConflictRuleServiceImpl(
            conflictRuleMapper, userRoleMapper, roleResourcePermissionMapper, resourceEntityMapper
        );
    }

    @Test
    @DisplayName("TC-E2E-01: sync → check → allow")
    void fullAuthorizationFlow() {
        Long tenantId = 1L;

        when(abstractUserMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { ((PcAbstractUser) inv.getArgument(0)).setId(10L); return 1; })
            .when(abstractUserMapper).insert(any(PcAbstractUser.class));

        SyncUsersReq usersReq = new SyncUsersReq();
        usersReq.setTenantId(tenantId);
        usersReq.setUserType(1);
        SyncUsersReq.SyncUserItem ui = new SyncUsersReq.SyncUserItem();
        ui.setExternalId("user1");
        ui.setName("User One");
        usersReq.setItems(Collections.singletonList(ui));
        syncService.syncUsers(usersReq);

        when(abstractRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { ((PcAbstractRole) inv.getArgument(0)).setId(20L); return 1; })
            .when(abstractRoleMapper).insert(any(PcAbstractRole.class));
        when(abstractRoleMapper.updateById(any(PcAbstractRole.class))).thenReturn(1);

        SyncRolesReq rolesReq = new SyncRolesReq();
        rolesReq.setTenantId(tenantId);
        SyncRolesReq.SyncRoleItem ri = new SyncRolesReq.SyncRoleItem();
        ri.setExternalId("role1");
        ri.setRoleType(1);
        ri.setName("Role One");
        rolesReq.setItems(Collections.singletonList(ri));
        syncService.syncRoles(rolesReq);

        when(resourceEntityMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { ((PcResourceEntity) inv.getArgument(0)).setId(30L); return 1; })
            .when(resourceEntityMapper).insert(any(PcResourceEntity.class));
        when(resourceEntityMapper.updateById(any(PcResourceEntity.class))).thenReturn(1);

        SyncResourcesReq resourcesReq = new SyncResourcesReq();
        resourcesReq.setTenantId(tenantId);
        SyncResourcesReq.SyncResourceItem rsi = new SyncResourcesReq.SyncResourceItem();
        rsi.setCode("RES_ORDER");
        rsi.setName("Order");
        resourcesReq.setItems(Collections.singletonList(rsi));
        syncService.syncResources(resourcesReq);

        PcOperationPermission viewOp = new PcOperationPermission();
        viewOp.setId(40L);
        viewOp.setTenantId(tenantId);
        viewOp.setCode("VIEW");
        viewOp.setName("View");
        viewOp.setDeleteFlag(NOT_DELETED);

        PcAbstractUser user = newUser(10L, tenantId, 1, "user1");
        PcAbstractRole role = newRole(20L, tenantId, "role1", null);

        when(abstractUserMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(user));
        when(abstractRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(role));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { ((PcUserRole) inv.getArgument(0)).setId(50L); return 1; })
            .when(userRoleMapper).insert(any(PcUserRole.class));

        SyncUserRolesReq urReq = new SyncUserRolesReq();
        urReq.setTenantId(tenantId);
        SyncUserRolesReq.SyncUserRoleItem uri = new SyncUserRolesReq.SyncUserRoleItem();
        uri.setUserExternalId("user1");
        uri.setUserType(1);
        uri.setRoleExternalId("role1");
        urReq.setItems(Collections.singletonList(uri));
        syncService.syncUserRoles(urReq);

        when(abstractRoleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        PcResourceEntity resource = newResource(30L, tenantId, "RES_ORDER");
        when(resourceEntityMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(resource));
        when(operationPermissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(viewOp));
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { ((PcRoleResourcePermission) inv.getArgument(0)).setId(60L); return 1; })
            .when(roleResourcePermissionMapper).insert(any(PcRoleResourcePermission.class));

        SyncRolePermissionsReq rpReq = new SyncRolePermissionsReq();
        rpReq.setTenantId(tenantId);
        rpReq.setRoleExternalId("role1");
        SyncRolePermissionsReq.SyncRolePermissionItem rpi = new SyncRolePermissionsReq.SyncRolePermissionItem();
        rpi.setResourceCode("RES_ORDER");
        rpi.setOperationCode("VIEW");
        rpReq.setItems(Collections.singletonList(rpi));
        syncService.syncRolePermissions(rpReq);

        PcUserRole userRole = newUserRole(50L, tenantId, 10L, 20L);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(userRole));
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));

        PcRoleResourcePermission grant = newGrant(60L, tenantId, 20L, 30L, 40L);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckReq checkReq = new PermissionCheckReq();
        checkReq.setTenantId(tenantId);
        checkReq.setAbstractUserId(10L);
        checkReq.setResourceEntityId(30L);
        checkReq.setOperationPermissionId(40L);

        PermissionCheckVo result = checkService.check(checkReq);

        assertTrue(result.getAllowed());
    }

    @Test
    @DisplayName("TC-E2E-02: revoke → check → deny")
    void revokeThenDeny() {
        Long tenantId = 1L;
        Long userId = 100L;
        Long roleId = 200L;

        UserRoleRevokeReq revokeReq = new UserRoleRevokeReq();
        revokeReq.setTenantId(tenantId);
        revokeReq.setAbstractUserId(userId);
        revokeReq.setRoleIds(Collections.singletonList(roleId));
        userRoleService.revoke(revokeReq);

        verify(permissionService).revokeUserRoles(any(UserRoleBatchRevokeRequest.class));

        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckReq checkReq = new PermissionCheckReq();
        checkReq.setTenantId(tenantId);
        checkReq.setAbstractUserId(userId);
        checkReq.setResourceEntityId(30L);
        checkReq.setOperationPermissionId(40L);

        PermissionCheckVo result = checkService.check(checkReq);

        assertFalse(result.getAllowed());
        assertEquals("无角色", result.getReason());
    }

    @Test
    @DisplayName("TC-E2E-03: conflict detection → non-empty violations")
    void conflictDetectionFlow() {
        Long tenantId = 1L;
        Long userId = 10L;
        Long roleId = 20L;
        Long resourceId = 30L;

        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(1L);
        rule.setTenantId(tenantId);
        rule.setFirstOperationPermissionId(40L);
        rule.setSecondOperationPermissionId(41L);
        rule.setDeleteFlag(NOT_DELETED);
        when(conflictRuleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(rule));

        PcUserRole ur = newUserRole(50L, tenantId, userId, roleId);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(ur));

        PcRoleResourcePermission rrp1 = newGrant(60L, tenantId, roleId, resourceId, 40L);
        PcRoleResourcePermission rrp2 = newGrant(61L, tenantId, roleId, resourceId, 41L);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(rrp1, rrp2));

        ConflictDetectReq detectReq = new ConflictDetectReq();
        detectReq.setTenantId(tenantId);
        detectReq.setAbstractUserId(userId);

        List<ConflictViolationVo> violations = conflictRuleService.detect(detectReq);

        assertFalse(violations.isEmpty());
        assertEquals(userId, violations.get(0).getAbstractUserId());
        assertEquals(resourceId, violations.get(0).getResourceEntityId());
    }

    @Test
    @DisplayName("TC-E2E-04: cascade delete role → check → deny")
    void cascadeDeleteThenDeny() {
        Long tenantId = 1L;

        PcUserRole ur = newUserRole(50L, tenantId, 10L, 20L);
        PcAbstractRole role = newRole(20L, tenantId, "role1", null);
        PcRoleResourcePermission grant = newGrant(60L, tenantId, 20L, 30L, 40L);

        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(ur));
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckReq checkReq = new PermissionCheckReq();
        checkReq.setTenantId(tenantId);
        checkReq.setAbstractUserId(10L);
        checkReq.setResourceEntityId(30L);
        checkReq.setOperationPermissionId(40L);

        assertTrue(checkService.check(checkReq).getAllowed());

        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo denied = checkService.check(checkReq);

        assertFalse(denied.getAllowed());
        assertEquals("无角色", denied.getReason());
    }

    @Test
    @DisplayName("TC-E2E-05: deleteNotInList removes absent user-role")
    void deleteNotInListRemovesAbsent() {
        Long tenantId = 1L;

        PcAbstractUser user = newUser(10L, tenantId, 1, "user1");
        when(abstractUserMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(user));

        PcAbstractRole role = newRole(20L, tenantId, "role1", null);
        when(abstractRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(role));

        PcUserRole staleUr = newUserRole(999L, tenantId, 10L, 99L);

        when(userRoleMapper.selectList(any(Wrapper.class)))
            .thenReturn(Collections.emptyList())
            .thenReturn(Collections.singletonList(staleUr));
        doAnswer(inv -> {
            ((PcUserRole) inv.getArgument(0)).setId(idSeq.getAndIncrement());
            return 1;
        }).when(userRoleMapper).insert(any(PcUserRole.class));
        when(userRoleMapper.updateById(any(PcUserRole.class))).thenReturn(1);

        SyncUserRolesReq req = new SyncUserRolesReq();
        req.setTenantId(tenantId);
        req.setDeleteNotInList(true);
        SyncUserRolesReq.SyncUserRoleItem item = new SyncUserRolesReq.SyncUserRoleItem();
        item.setUserExternalId("user1");
        item.setUserType(1);
        item.setRoleExternalId("role1");
        req.setItems(Collections.singletonList(item));

        syncService.syncUserRoles(req);

        assertEquals(999L, staleUr.getDeleteFlag());
        assertNotNull(staleUr.getDeletedAt());
    }

    @Test
    @DisplayName("TC-E2E-06: multi-tenant isolation → tenant2 deny")
    void multiTenantIsolation() {
        Long tenant1 = 1L;
        Long tenant2 = 2L;

        PcUserRole ur = newUserRole(50L, tenant1, 10L, 20L);
        PcAbstractRole role = newRole(20L, tenant1, "role1", null);
        PcRoleResourcePermission grant = newGrant(60L, tenant1, 20L, 30L, 40L);

        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(ur));
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckReq t1Req = new PermissionCheckReq();
        t1Req.setTenantId(tenant1);
        t1Req.setAbstractUserId(10L);
        t1Req.setResourceEntityId(30L);
        t1Req.setOperationPermissionId(40L);

        assertTrue(checkService.check(t1Req).getAllowed());

        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckReq t2Req = new PermissionCheckReq();
        t2Req.setTenantId(tenant2);
        t2Req.setAbstractUserId(10L);
        t2Req.setResourceEntityId(30L);
        t2Req.setOperationPermissionId(40L);

        PermissionCheckVo result = checkService.check(t2Req);

        assertFalse(result.getAllowed());
        assertEquals("无角色", result.getReason());
    }

    @Test
    @DisplayName("TC-E2E-07: multi-domain isolation → domain200 deny")
    void multiDomainIsolation() {
        Long tenantId = 1L;

        PcUserRole ur = newUserRole(50L, tenantId, 10L, 20L);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(ur));

        PcAbstractRole role = newRole(20L, tenantId, "role1", 100L);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));

        PermissionCheckReq checkReq = new PermissionCheckReq();
        checkReq.setTenantId(tenantId);
        checkReq.setAbstractUserId(10L);
        checkReq.setResourceEntityId(30L);
        checkReq.setOperationPermissionId(40L);
        checkReq.setBizDomainId(200L);

        PermissionCheckVo result = checkService.check(checkReq);

        assertFalse(result.getAllowed());
        assertEquals("无角色", result.getReason());
    }

    @Test
    @DisplayName("TC-E2E-08: dependency chain → allow then deny")
    void dependencyChain() {
        Long tenantId = 1L;
        Long roleId = 20L;
        Long resourceA = 30L;
        Long resourceB = 31L;
        Long opView = 40L;
        Long opRead = 41L;

        PcUserRole ur = newUserRole(50L, tenantId, 10L, roleId);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(ur));

        PcAbstractRole role = newRole(roleId, tenantId, "role1", null);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(role));

        PcRoleResourcePermission grantA = newGrant(60L, tenantId, roleId, resourceA, opView);
        PcRoleResourcePermission grantB = newGrant(61L, tenantId, roleId, resourceB, opRead);

        PcResourceDependency dep = new PcResourceDependency();
        dep.setId(1L);
        dep.setTenantId(tenantId);
        dep.setResourceEntityId(resourceA);
        dep.setDependsOnResourceEntityId(resourceB);
        dep.setSourceOperationPermissionId(opView);
        dep.setRequiredOperationPermissionId(opRead);
        dep.setDeleteFlag(NOT_DELETED);

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(Collections.singletonList(grantA))
            .thenReturn(Collections.singletonList(grantB));
        when(resourceDependencyMapper.selectList(any(Wrapper.class)))
            .thenReturn(Collections.singletonList(dep))
            .thenReturn(Collections.emptyList());

        PermissionCheckReq checkReq = new PermissionCheckReq();
        checkReq.setTenantId(tenantId);
        checkReq.setAbstractUserId(10L);
        checkReq.setResourceEntityId(resourceA);
        checkReq.setOperationPermissionId(opView);

        PermissionCheckVo allowed = checkService.check(checkReq);
        assertTrue(allowed.getAllowed());

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(Collections.singletonList(grantA))
            .thenReturn(Collections.emptyList());
        when(resourceDependencyMapper.selectList(any(Wrapper.class)))
            .thenReturn(Collections.singletonList(dep));

        PermissionCheckVo denied = checkService.check(checkReq);
        assertFalse(denied.getAllowed());
        assertEquals("依赖不满足", denied.getReason());
    }

    private PcAbstractUser newUser(Long id, Long tenantId, Integer userType, String externalId) {
        PcAbstractUser u = new PcAbstractUser();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setUserType(userType);
        u.setExternalId(externalId);
        u.setDeleteFlag(NOT_DELETED);
        return u;
    }

    private PcAbstractRole newRole(Long id, Long tenantId, String externalId, Long bizDomainId) {
        PcAbstractRole r = new PcAbstractRole();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setExternalId(externalId);
        r.setBizDomainId(bizDomainId);
        r.setDeleteFlag(NOT_DELETED);
        return r;
    }

    private PcResourceEntity newResource(Long id, Long tenantId, String code) {
        PcResourceEntity re = new PcResourceEntity();
        re.setId(id);
        re.setTenantId(tenantId);
        re.setCode(code);
        re.setDeleteFlag(NOT_DELETED);
        return re;
    }

    private PcUserRole newUserRole(Long id, Long tenantId, Long userId, Long roleId) {
        PcUserRole ur = new PcUserRole();
        ur.setId(id);
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(userId);
        ur.setAbstractRoleId(roleId);
        ur.setDeleteFlag(NOT_DELETED);
        return ur;
    }

    private PcRoleResourcePermission newGrant(Long id, Long tenantId, Long roleId, Long resourceId, Long opId) {
        PcRoleResourcePermission rrp = new PcRoleResourcePermission();
        rrp.setId(id);
        rrp.setTenantId(tenantId);
        rrp.setAbstractRoleId(roleId);
        rrp.setResourceEntityId(resourceId);
        rrp.setOperationPermissionId(opId);
        rrp.setDeleteFlag(NOT_DELETED);
        return rrp;
    }
}
