package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionConflictRule;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.ConflictRuleListReq;
import org.dromara.permission.domain.dto.ConflictRuleSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.ConflictRuleVo;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ConflictRuleServiceImplTest {

    @Mock
    private PcPermissionConflictRuleMapper conflictRuleMapper;
    @Mock
    private PcUserRoleMapper userRoleMapper;
    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private PcResourceEntityMapper resourceEntityMapper;

    @InjectMocks
    private ConflictRuleServiceImpl service;

    private PcPermissionConflictRule buildRule(Long id, Long tenantId, Long bizDomainId,
                                               Long firstOpId, Long secondOpId, Integer resType) {
        PcPermissionConflictRule r = new PcPermissionConflictRule();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setBizDomainId(bizDomainId);
        r.setFirstOperationPermissionId(firstOpId);
        r.setSecondOperationPermissionId(secondOpId);
        r.setResourceTypeValue(resType);
        r.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return r;
    }

    private PcUserRole buildUserRole(Long id, Long tenantId, Long userId, Long roleId) {
        PcUserRole ur = new PcUserRole();
        ur.setId(id);
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(userId);
        ur.setAbstractRoleId(roleId);
        ur.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return ur;
    }

    private PcRoleResourcePermission buildRrp(Long id, Long tenantId, Long roleId,
                                               Long resourceId, Long opId) {
        PcRoleResourcePermission rrp = new PcRoleResourcePermission();
        rrp.setId(id);
        rrp.setTenantId(tenantId);
        rrp.setAbstractRoleId(roleId);
        rrp.setResourceEntityId(resourceId);
        rrp.setOperationPermissionId(opId);
        rrp.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return rrp;
    }

    // ── list ──

    @Test
    void list_normal() {
        PcPermissionConflictRule rule = buildRule(1L, 100L, 10L, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rule));

        ConflictRuleListReq req = new ConflictRuleListReq();
        req.setTenantId(100L);
        List<ConflictRuleVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getFirstOperationPermissionId());
        assertEquals(2L, result.get(0).getSecondOperationPermissionId());
    }

    @Test
    void list_nullTenantId() {
        ConflictRuleListReq req = new ConflictRuleListReq();
        List<ConflictRuleVo> result = service.list(req);
        assertTrue(result.isEmpty());
        verifyNoInteractions(conflictRuleMapper);
    }

    // ── save ──

    @Test
    void save_new() {
        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(1L);
        req.setSecondOperationPermissionId(2L);

        service.save(req);

        ArgumentCaptor<PcPermissionConflictRule> captor = ArgumentCaptor.forClass(PcPermissionConflictRule.class);
        verify(conflictRuleMapper).insert(captor.capture());
        PcPermissionConflictRule saved = captor.getValue();
        assertEquals(100L, saved.getTenantId());
        assertEquals(1L, saved.getFirstOperationPermissionId());
        assertEquals(2L, saved.getSecondOperationPermissionId());
        assertEquals(PermissionConstants.NOT_DELETED, saved.getDeleteFlag());
    }

    @Test
    void save_update() {
        PcPermissionConflictRule existing = buildRule(10L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setId(10L);
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(3L);
        req.setSecondOperationPermissionId(4L);
        req.setResourceTypeValue(1);

        service.save(req);

        verify(conflictRuleMapper).updateById(existing);
        assertEquals(3L, existing.getFirstOperationPermissionId());
        assertEquals(4L, existing.getSecondOperationPermissionId());
        assertEquals(1, existing.getResourceTypeValue());
    }

    @Test
    void save_firstEqualsSecond() {
        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(5L);
        req.setSecondOperationPermissionId(5L);

        service.save(req);

        verify(conflictRuleMapper, never()).insert(any(PcPermissionConflictRule.class));
        verify(conflictRuleMapper, never()).updateById(any(PcPermissionConflictRule.class));
    }

    @Test
    void save_firstGreaterThanSecond() {
        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(10L);
        req.setSecondOperationPermissionId(3L);

        service.save(req);

        ArgumentCaptor<PcPermissionConflictRule> captor = ArgumentCaptor.forClass(PcPermissionConflictRule.class);
        verify(conflictRuleMapper).insert(captor.capture());
        assertEquals(3L, captor.getValue().getFirstOperationPermissionId());
        assertEquals(10L, captor.getValue().getSecondOperationPermissionId());
    }

    @Test
    void save_nullTenantId() {
        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setFirstOperationPermissionId(1L);
        req.setSecondOperationPermissionId(2L);

        service.save(req);

        verifyNoInteractions(conflictRuleMapper);
    }

    // ── remove ──

    @Test
    void remove_normal() {
        PcPermissionConflictRule entity = buildRule(5L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(entity));

        IdsReq req = new IdsReq();
        req.setTenantId(100L);
        req.setIds(Collections.singletonList(5L));

        service.remove(req);

        verify(conflictRuleMapper).updateById(entity);
        assertEquals(5L, entity.getDeleteFlag());
        assertNotNull(entity.getDeletedAt());
    }

    @Test
    void remove_tenantIsolation() {
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        IdsReq req = new IdsReq();
        req.setTenantId(999L);
        req.setIds(Collections.singletonList(5L));

        service.remove(req);

        verify(conflictRuleMapper, never()).updateById(any(PcPermissionConflictRule.class));
    }

    // ── detect ──

    @Test
    void detect_userLevelConflict() {
        PcPermissionConflictRule rule = buildRule(1L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rule));

        PcUserRole ur = buildUserRole(10L, 100L, 50L, 30L);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(ur));

        List<PcRoleResourcePermission> rrps = Arrays.asList(
            buildRrp(100L, 100L, 30L, 200L, 1L),
            buildRrp(101L, 100L, 30L, 200L, 2L)
        );
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(rrps);

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractUserId(50L);

        List<ConflictViolationVo> result = service.detect(req);

        assertFalse(result.isEmpty());
        assertEquals(50L, result.get(0).getAbstractUserId());
        assertEquals(200L, result.get(0).getResourceEntityId());
    }

    @Test
    void detect_roleLevelConflict() {
        PcPermissionConflictRule rule = buildRule(1L, 100L, null, 3L, 4L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rule));

        List<PcRoleResourcePermission> rrps = Arrays.asList(
            buildRrp(100L, 100L, 30L, 200L, 3L),
            buildRrp(101L, 100L, 30L, 200L, 4L)
        );
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(rrps);

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractRoleId(30L);

        List<ConflictViolationVo> result = service.detect(req);

        assertFalse(result.isEmpty());
        assertEquals(30L, result.get(0).getAbstractRoleId());
        assertTrue(result.get(0).getDescription().contains("角色"));
    }

    @Test
    void detect_bothUserAndRole() {
        PcPermissionConflictRule rule = buildRule(1L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rule));

        PcUserRole ur = buildUserRole(10L, 100L, 50L, 30L);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(ur));

        List<PcRoleResourcePermission> rrps = Arrays.asList(
            buildRrp(100L, 100L, 30L, 200L, 1L),
            buildRrp(101L, 100L, 30L, 200L, 2L)
        );
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(rrps);

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractUserId(50L);
        req.setAbstractRoleId(30L);

        List<ConflictViolationVo> result = service.detect(req);

        long userViolations = result.stream().filter(v -> v.getAbstractUserId() != null).count();
        long roleViolations = result.stream().filter(v -> v.getAbstractRoleId() != null).count();
        assertTrue(userViolations > 0);
        assertTrue(roleViolations > 0);
    }

    @Test
    void detect_noRules() {
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractUserId(50L);

        List<ConflictViolationVo> result = service.detect(req);

        assertTrue(result.isEmpty());
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    void detect_noConflict() {
        PcPermissionConflictRule rule = buildRule(1L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rule));

        PcUserRole ur = buildUserRole(10L, 100L, 50L, 30L);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(ur));

        List<PcRoleResourcePermission> rrps = Collections.singletonList(
            buildRrp(100L, 100L, 30L, 200L, 1L)
        );
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(rrps);

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractUserId(50L);

        List<ConflictViolationVo> result = service.detect(req);

        assertTrue(result.isEmpty());
    }

    @Test
    void detect_resourceEntityIdFilter() {
        PcPermissionConflictRule rule = buildRule(1L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rule));

        PcUserRole ur = buildUserRole(10L, 100L, 50L, 30L);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(ur));

        List<PcRoleResourcePermission> rrps = Arrays.asList(
            buildRrp(100L, 100L, 30L, 200L, 1L),
            buildRrp(101L, 100L, 30L, 200L, 2L),
            buildRrp(102L, 100L, 30L, 300L, 1L),
            buildRrp(103L, 100L, 30L, 300L, 2L)
        );
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(rrps);

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractUserId(50L);
        req.setResourceEntityId(200L);

        List<ConflictViolationVo> result = service.detect(req);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(v -> v.getResourceEntityId().equals(200L)));
    }

    @Test
    void detect_nullTenantId() {
        ConflictDetectReq req = new ConflictDetectReq();

        List<ConflictViolationVo> result = service.detect(req);

        assertTrue(result.isEmpty());
        verifyNoInteractions(conflictRuleMapper);
    }
}
