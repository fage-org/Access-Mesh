package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

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
    void check_bizDomainIdFilter() {
        baseReq.setBizDomainId(BIZ_DOMAIN);

        PcUserRole ur1 = buildUserRole(ROLE_ID);
        PcUserRole ur2 = buildUserRole(201L);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur1, ur2));

        PcAbstractRole roleMatch = buildAbstractRole(ROLE_ID, BIZ_DOMAIN);
        PcAbstractRole roleOther = buildAbstractRole(201L, 999L);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(roleMatch, roleOther));

        PcRoleResourcePermission grant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_hasGrantNoCondition_allow() {
        stubRolesResolved();
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null)));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
        assertNull(vo.getReason());
    }

    @Test
    void check_hasGrantWithApprovedBlankCondition_allow() {
        stubRolesResolved();
        Long condId = 500L;
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, condId)));
        when(permissionConditionMapper.selectById(condId)).thenReturn(buildCondition(condId, "", PermissionConstants.CONDITION_STATUS_APPROVED));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_hasGrantWithApprovedContextCondition_allow() {
        stubRolesResolved();
        Long condId = 500L;
        baseReq.setContext(Map.of("condition:WORKDAY_ONLY", true));
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, condId)));
        PcPermissionCondition condition = buildCondition(condId, "WORKDAY_ONLY", PermissionConstants.CONDITION_STATUS_APPROVED);
        condition.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        when(permissionConditionMapper.selectById(condId)).thenReturn(condition);
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
    }

    @Test
    void check_hasGrantWithPendingCondition_denies() {
        stubRolesResolved();
        Long condId = 500L;
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, condId)));
        when(permissionConditionMapper.selectById(condId))
            .thenReturn(buildCondition(condId, "", PermissionConstants.CONDITION_STATUS_PENDING));

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
    void check_dependencySatisfied_allow() {
        stubRolesResolved();

        Long depResId = 301L;
        Long depOpId = 401L;

        PcResourceDependency dep = buildDependency(RESOURCE_ID, depResId, OP_ID, depOpId);
        PcRoleResourcePermission mainGrant = buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null);
        PcRoleResourcePermission depGrant = buildGrant(ROLE_ID, depResId, depOpId, null);

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mainGrant), List.of(depGrant));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dep), Collections.emptyList());

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

        when(roleResourcePermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(mainGrant), Collections.emptyList());
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dep));

        PermissionCheckVo vo = service.check(baseReq);
        assertFalse(vo.getAllowed());
        assertEquals("依赖不满足", vo.getReason());
    }

    @Test
    void check_fullFlow_allow() {
        stubRolesResolved();
        when(roleResourcePermissionMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(buildGrant(ROLE_ID, RESOURCE_ID, OP_ID, null)));
        when(resourceDependencyMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        PermissionCheckVo vo = service.check(baseReq);
        assertTrue(vo.getAllowed());
        assertNull(vo.getReason());
    }

    private void stubRolesResolved() {
        PcUserRole ur = buildUserRole(ROLE_ID);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(buildAbstractRole(ROLE_ID, null)));
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
        PcAbstractRole role = new PcAbstractRole();
        role.setId(id);
        role.setTenantId(TENANT);
        role.setBizDomainId(bizDomainId);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }

    private PcRoleResourcePermission buildGrant(Long roleId, Long resourceId, Long opId, Long conditionId) {
        PcRoleResourcePermission grant = new PcRoleResourcePermission();
        grant.setId(1L);
        grant.setTenantId(TENANT);
        grant.setAbstractRoleId(roleId);
        grant.setResourceEntityId(resourceId);
        grant.setOperationPermissionId(opId);
        grant.setConditionId(conditionId);
        grant.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return grant;
    }

    private PcPermissionCondition buildCondition(Long id, String expression, String status) {
        PcPermissionCondition condition = new PcPermissionCondition();
        condition.setId(id);
        condition.setTenantId(TENANT);
        condition.setCode("WORKDAY_ONLY");
        condition.setExpression(expression);
        condition.setStatus(status);
        condition.setConditionSource(PermissionConstants.CONDITION_SOURCE_CUSTOM);
        condition.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return condition;
    }

    private PcResourceDependency buildDependency(Long resId, Long depResId, Long srcOpId, Long reqOpId) {
        PcResourceDependency dependency = new PcResourceDependency();
        dependency.setId(1L);
        dependency.setTenantId(TENANT);
        dependency.setResourceEntityId(resId);
        dependency.setDependsOnResourceEntityId(depResId);
        dependency.setSourceOperationPermissionId(srcOpId);
        dependency.setRequiredOperationPermissionId(reqOpId);
        dependency.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return dependency;
    }
}
