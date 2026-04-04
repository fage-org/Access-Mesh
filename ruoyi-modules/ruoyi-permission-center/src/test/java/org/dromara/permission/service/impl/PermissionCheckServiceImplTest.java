package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionCheckServiceImplTest {

    @Mock
    private PcUserRoleMapper userRoleMapper;
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;
    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private PcPermissionConditionMapper permissionConditionMapper;
    @Mock
    private PcResourceDependencyMapper resourceDependencyMapper;

    @InjectMocks
    private PermissionCheckServiceImpl service;

    private static final Long TENANT = 1L;
    private static final Long USER_ID = 100L;
    private static final Long ROLE_ID = 200L;
    private static final Long RESOURCE_ID = 300L;
    private static final Long OP_ID = 400L;
    private static final Long BIZ_DOMAIN = 10L;

    private PermissionCheckReq baseReq;

    @BeforeEach
    void setUp() {
        baseReq = new PermissionCheckReq();
        baseReq.setTenantId(TENANT);
        baseReq.setAbstractUserId(USER_ID);
        baseReq.setResourceEntityId(RESOURCE_ID);
        baseReq.setOperationPermissionId(OP_ID);
    }

    // ======================== Parameter validation ========================

    @Test
    void check_nullReq_returnsDeny() {
        PermissionCheckVo vo = service.check(null);
        assertFalse(vo.getAllowed());
        assertEquals("参数不完整", vo.getReason());
    }

    @Test
    void check_nullTenantId_returnsDeny() {
        baseReq.setTenantId(null);
        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("参数不完整", vo.getReason());
    }

    @Test
    void check_nullAbstractUserId_returnsDeny() {
        baseReq.setAbstractUserId(null);
        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("参数不完整", vo.getReason());
    }

    @Test
    void check_nullResourceEntityId_returnsDeny() {
        baseReq.setResourceEntityId(null);
        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("参数不完整", vo.getReason());
    }

    @Test
    void check_nullOperationPermissionId_returnsDeny() {
        baseReq.setOperationPermissionId(null);
        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("参数不完整", vo.getReason());
    }

    // ======================== Role resolution ========================

    @Test
    void check_noRoles_returnsDenyNoRole() {
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("无角色", vo.getReason());
    }

    @Test
    void check_roleValidFromInFuture_filtered() {
        PcUserRole ur = buildUserRole(ROLE_ID);
        ur.setValidFrom(LocalDateTime.now().plusDays(1));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("无角色", vo.getReason());
    }

    @Test
    void check_roleValidToInPast_filtered() {
        PcUserRole ur = buildUserRole(ROLE_ID);
        ur.setValidTo(LocalDateTime.now().minusDays(1));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("无角色", vo.getReason());
    }

    @Test
    void check_roleValidFromNull_roleValid() {
        PcUserRole ur = buildUserRole(ROLE_ID);
        ur.setValidFrom(null);
        ur.setValidTo(null);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));

        PcAbstractRole role = buildAbstractRole(ROLE_ID, null);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));

        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_bizDomainIdFilter() {
        baseReq.setBizDomainId(BIZ_DOMAIN);

        PcUserRole ur1 = buildUserRole(ROLE_ID);
        Long otherRoleId = 201L;
        PcUserRole ur2 = buildUserRole(otherRoleId);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur1, ur2));

        PcAbstractRole roleMatch = buildAbstractRole(ROLE_ID, BIZ_DOMAIN);
        PcAbstractRole roleOther = buildAbstractRole(otherRoleId, 999L);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(roleMatch, roleOther));

        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    // ======================== Grant check ========================

    @Test
    void check_hasGrantNoCondition_allow() {
        stubRolesResolved();
        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
        assertNull(vo.getReason());
    }

    @Test
    void check_hasGrantWithCondition_skipped() {
        stubRolesResolved();

        Long condId = 500L;
        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, condId);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));

        PcPermissionCondition cond = new PcPermissionCondition();
        cond.setId(condId);
        cond.setDeleteFlag(PermissionConstants.NOT_DELETED);
        cond.setExpression("user.level > 5");
        when(permissionConditionMapper.selectById(condId)).thenReturn(cond);

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("无授权", vo.getReason());
    }

    @Test
    void check_noGrant_deny() {
        stubRolesResolved();
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("无授权", vo.getReason());
    }

    @Test
    void check_multipleGrants_firstWithConditionSecondWithout_allow() {
        stubRolesResolved();

        Long condId = 500L;
        PcRoleResourcePermission g1 = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, condId);
        PcRoleResourcePermission g2 = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(g1, g2));

        PcPermissionCondition cond = new PcPermissionCondition();
        cond.setId(condId);
        cond.setDeleteFlag(PermissionConstants.NOT_DELETED);
        cond.setExpression("user.level > 5");
        when(permissionConditionMapper.selectById(condId)).thenReturn(cond);

        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    // ======================== Dependency check ========================

    @Test
    void check_noDependencies_allow() {
        stubRolesResolved();
        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_dependencySatisfied_allow() {
        stubRolesResolved();

        Long depResId = 301L;
        Long depOpId = 401L;

        PcResourceDependency dep = buildDependency(RESOURCE_ID, depResId, OP_ID, depOpId);
        PcRoleResourcePermission mainGrant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        PcRoleResourcePermission depGrant = buildGrant(ROLE_ID, depResId, depOpId, null);

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mainGrant))
            .thenReturn(List.of(depGrant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dep))
            .thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_dependencyNotSatisfied_deny() {
        stubRolesResolved();

        Long depResId = 301L;
        Long depOpId = 401L;

        PcResourceDependency dep = buildDependency(RESOURCE_ID, depResId, OP_ID, depOpId);
        PcRoleResourcePermission mainGrant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mainGrant))
            .thenReturn(Collections.emptyList());
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dep));

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("依赖不满足", vo.getReason());
    }

    @Test
    void check_nestedDependency_allow() {
        stubRolesResolved();

        Long resB = 301L;
        Long opB = 401L;
        Long resC = 302L;
        Long opC = 402L;

        PcResourceDependency depAB = buildDependency(RESOURCE_ID, resB, OP_ID, opB);
        PcResourceDependency depBC = buildDependency(resB, resC, opB, opC);

        PcRoleResourcePermission grantA = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        PcRoleResourcePermission grantB = buildGrant(ROLE_ID, resB, opB, null);
        PcRoleResourcePermission grantC = buildGrant(ROLE_ID, resC, opC, null);

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(grantA))
            .thenReturn(List.of(grantB))
            .thenReturn(List.of(grantC));
        when(resourceDependencyMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(depAB))
            .thenReturn(List.of(depBC))
            .thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_depthExceedsLimit_deny() {
        stubRolesResolved();

        PcRoleResourcePermission mainGrant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mainGrant));

        PcResourceDependency selfDep = buildDependency(RESOURCE_ID, RESOURCE_ID, OP_ID, OP_ID);
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(selfDep));

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("依赖不满足", vo.getReason());
    }

    // ======================== Full flow ========================

    @Test
    void check_fullFlow_allow() {
        PcUserRole ur = buildUserRole(ROLE_ID);
        ur.setValidFrom(LocalDateTime.now().minusDays(1));
        ur.setValidTo(LocalDateTime.now().plusDays(1));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));

        PcAbstractRole role = buildAbstractRole(ROLE_ID, null);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));

        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));

        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
        assertNull(vo.getReason());
    }

    // ======================== Helpers ========================

    private void stubRolesResolved() {
        PcUserRole ur = buildUserRole(ROLE_ID);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));
        PcAbstractRole role = buildAbstractRole(ROLE_ID, null);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));
    }

    private PcUserRole buildUserRole(Long roleId) {
        PcUserRole ur = new PcUserRole();
        ur.setId(1L);
        ur.setTenantId(TENANT);
        ur.setAbstractUserId(USER_ID);
        ur.setAbstractRoleId(roleId);
        ur.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return ur;
    }

    private PcAbstractRole buildAbstractRole(Long id, Long bizDomainId) {
        PcAbstractRole r = new PcAbstractRole();
        r.setId(id);
        r.setTenantId(TENANT);
        r.setBizDomainId(bizDomainId);
        r.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return r;
    }

    private PcRoleResourcePermission buildGrant(Long roleId, Long resourceId, Long opId, Long conditionId) {
        PcRoleResourcePermission g = new PcRoleResourcePermission();
        g.setId(1L);
        g.setTenantId(TENANT);
        g.setAbstractRoleId(roleId);
        g.setResourceEntityId(resourceId);
        g.setOperationPermissionId(opId);
        g.setConditionId(conditionId);
        g.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return g;
    }

    private PcResourceDependency buildDependency(Long resId, Long depResId, Long srcOpId, Long reqOpId) {
        PcResourceDependency d = new PcResourceDependency();
        d.setId(1L);
        d.setTenantId(TENANT);
        d.setResourceEntityId(resId);
        d.setDependsOnResourceEntityId(depResId);
        d.setSourceOperationPermissionId(srcOpId);
        d.setRequiredOperationPermissionId(reqOpId);
        d.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return d;
    }
}
