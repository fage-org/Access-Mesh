package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionConflictRule;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.ConflictRuleListReq;
import org.dromara.permission.domain.dto.ConflictRuleSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.ConflictDetectionPageVo;
import org.dromara.permission.domain.vo.ConflictRuleVo;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.event.PermissionGovernanceEventPublisher;
import org.dromara.permission.event.PermissionWriteRefreshEventPublisher;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionVersionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.dromara.permission.service.support.PermissionBridgeSupport;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;
    @Mock
    private PermissionGovernanceEventPublisher governanceEventPublisher;
    @Mock
    private PermissionBridgeSupport permissionBridgeSupport;
    @Mock
    private PermissionVersionService permissionVersionService;
    @Mock
    private PermissionWriteRefreshEventPublisher permissionWriteRefreshEventPublisher;
    @Mock
    private PermissionChangeLogService permissionChangeLogService;

    @InjectMocks
    private ConflictRuleServiceImpl service;

    private PcPermissionConflictRule buildRule(Long id, Long tenantId, Long bizDomainId,
                                               Long firstOpId, Long secondOpId, Integer resourceType) {
        PcPermissionConflictRule rule = new PcPermissionConflictRule();
        rule.setId(id);
        rule.setTenantId(tenantId);
        rule.setBizDomainId(bizDomainId);
        rule.setFirstOperationPermissionId(firstOpId);
        rule.setSecondOperationPermissionId(secondOpId);
        rule.setResourceTypeValue(resourceType);
        rule.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return rule;
    }

    private PcUserRole buildUserRole(Long id, Long tenantId, Long userId, Long roleId) {
        PcUserRole userRole = new PcUserRole();
        userRole.setId(id);
        userRole.setTenantId(tenantId);
        userRole.setAbstractUserId(userId);
        userRole.setAbstractRoleId(roleId);
        userRole.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return userRole;
    }

    private PcRoleResourcePermission buildPermission(Long id, Long tenantId, Long roleId, Long resourceId, Long opId) {
        PcRoleResourcePermission permission = new PcRoleResourcePermission();
        permission.setId(id);
        permission.setTenantId(tenantId);
        permission.setAbstractRoleId(roleId);
        permission.setResourceEntityId(resourceId);
        permission.setOperationPermissionId(opId);
        permission.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return permission;
    }

    private PcAbstractRole buildRole(Long id, Long tenantId, Long bizDomainId) {
        PcAbstractRole role = new PcAbstractRole();
        role.setId(id);
        role.setTenantId(tenantId);
        role.setBizDomainId(bizDomainId);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }

    private PcResourceEntity buildResource(Long id, Long tenantId, Long bizDomainId, Integer resourceType) {
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(id);
        resource.setTenantId(tenantId);
        resource.setBizDomainId(bizDomainId);
        resource.setResourceType(resourceType);
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return resource;
    }

    private PcOperationPermission buildOperation(Long id, Long tenantId, Integer resourceType) {
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(id);
        operation.setTenantId(tenantId);
        operation.setResourceType(resourceType);
        operation.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return operation;
    }

    @Test
    void list_returnsRules() {
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(buildRule(1L, 100L, 10L, 1L, 2L, 3)));

        ConflictRuleListReq req = new ConflictRuleListReq();
        req.setTenantId(100L);
        req.setBizDomainId(10L);
        req.setResourceTypeValue(3);

        List<ConflictRuleVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getFirstOperationPermissionId());
        assertEquals(3, result.get(0).getResourceTypeValue());
    }

    @Test
    void save_newRule_insertsNormalizedPair() {
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(permissionBridgeSupport.loadOperation(100L, 2L)).thenReturn(buildOperation(2L, 100L, 3));
        when(permissionBridgeSupport.loadOperation(100L, 9L)).thenReturn(buildOperation(9L, 100L, 3));
        when(conflictRuleMapper.insert(any(PcPermissionConflictRule.class))).thenAnswer(invocation -> {
            PcPermissionConflictRule entity = invocation.getArgument(0);
            entity.setId(88L);
            return 1;
        });
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(100L);
        version.setVersionNo(9L);
        when(permissionVersionService.bumpVersion(100L, "permission_conflict_rule", 88L, "save-conflict-rule"))
            .thenReturn(version);
        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(9L);
        req.setSecondOperationPermissionId(2L);
        req.setResourceTypeValue(3);
        req.setRequestId("req-save-rule");
        req.setChangeReason("normalize");

        service.save(req);

        ArgumentCaptor<PcPermissionConflictRule> captor = ArgumentCaptor.forClass(PcPermissionConflictRule.class);
        verify(conflictRuleMapper).insert(captor.capture());
        assertEquals(2L, captor.getValue().getFirstOperationPermissionId());
        assertEquals(9L, captor.getValue().getSecondOperationPermissionId());
        assertEquals(3, captor.getValue().getResourceTypeValue());
        ArgumentCaptor<ChangeLogParam> changeLogCaptor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(permissionChangeLogService).writeChangeLog(changeLogCaptor.capture());
        assertEquals("permission_conflict_rule", changeLogCaptor.getValue().getEntityType());
        assertEquals("INSERT", changeLogCaptor.getValue().getOperation());
        assertEquals("req-save-rule", changeLogCaptor.getValue().getRequestId());
        verify(permissionVersionService).bumpVersion(100L, "permission_conflict_rule", 88L, "save-conflict-rule");
        verify(permissionWriteRefreshEventPublisher).publish(any(), eq(version));
    }

    @Test
    void save_operationTypesMismatch_throwsInvalidRequest() {
        when(permissionBridgeSupport.loadOperation(100L, 1L)).thenReturn(buildOperation(1L, 100L, 3));
        when(permissionBridgeSupport.loadOperation(100L, 2L)).thenReturn(buildOperation(2L, 100L, 4));

        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(1L);
        req.setSecondOperationPermissionId(2L);

        assertThrows(PermissionServiceException.class, () -> service.save(req));
        verify(conflictRuleMapper, never()).insert(any(PcPermissionConflictRule.class));
    }

    @Test
    void save_sameOperation_throwsInvalidRequest() {
        ConflictRuleSaveReq req = new ConflictRuleSaveReq();
        req.setTenantId(100L);
        req.setFirstOperationPermissionId(5L);
        req.setSecondOperationPermissionId(5L);

        assertThrows(PermissionServiceException.class, () -> service.save(req));
        verify(conflictRuleMapper, never()).insert(any(PcPermissionConflictRule.class));
    }

    @Test
    void remove_marksDeleted() {
        PcPermissionConflictRule entity = buildRule(5L, 100L, null, 1L, 2L, null);
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(100L);
        version.setVersionNo(4L);
        when(permissionVersionService.bumpVersion(100L, "permission_conflict_rule", 5L, "remove-conflict-rule"))
            .thenReturn(version);

        IdsReq req = new IdsReq();
        req.setTenantId(100L);
        req.setIds(List.of(5L));
        req.setRequestId("req-remove-rule");

        service.remove(req);

        verify(conflictRuleMapper).updateById(entity);
        assertEquals(5L, entity.getDeleteFlag());
        ArgumentCaptor<ChangeLogParam> changeLogCaptor = ArgumentCaptor.forClass(ChangeLogParam.class);
        verify(permissionChangeLogService).writeChangeLog(changeLogCaptor.capture());
        assertEquals("DELETE", changeLogCaptor.getValue().getOperation());
        assertEquals("req-remove-rule", changeLogCaptor.getValue().getRequestId());
        verify(permissionVersionService).bumpVersion(100L, "permission_conflict_rule", 5L, "remove-conflict-rule");
        verify(permissionWriteRefreshEventPublisher).publish(any(), eq(version));
    }

    @Test
    void detectPage_userScope_returnsPagedViolationsAndPublishesEvent() {
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(buildRule(1L, 100L, 10L, 1L, 2L, 7)));
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            buildPermission(10L, 100L, 30L, 200L, 1L),
            buildPermission(11L, 100L, 30L, 200L, 2L)
        ));
        when(resourceEntityMapper.selectBatchIds(any())).thenReturn(List.of(buildResource(200L, 100L, 10L, 7)));
        when(abstractRoleMapper.selectBatchIds(any())).thenReturn(List.of(buildRole(30L, 100L, 10L)));
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(buildUserRole(50L, 100L, 80L, 30L)));

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractUserId(80L);
        req.setBizDomainId(10L);
        req.setPageNum(1);
        req.setPageSize(10);
        req.setRequestId("req-conflict-1");

        ConflictDetectionPageVo page = service.detectPage(req);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getItems().size());
        ConflictViolationVo violation = page.getItems().get(0);
        assertEquals(80L, violation.getAbstractUserId());
        assertEquals(200L, violation.getResourceEntityId());
        assertEquals(1L, violation.getFirstOperationPermissionId());
        assertEquals(2L, violation.getSecondOperationPermissionId());
        verify(governanceEventPublisher).publishConflictDetected(req, page.getItems(), 1L);
    }

    @Test
    void detectPage_roleScope_respectsPagination() {
        when(conflictRuleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(buildRule(1L, 100L, null, 1L, 2L, 7)));
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            buildPermission(10L, 100L, 30L, 200L, 1L),
            buildPermission(11L, 100L, 30L, 200L, 2L),
            buildPermission(12L, 100L, 30L, 201L, 1L),
            buildPermission(13L, 100L, 30L, 201L, 2L)
        ));
        when(resourceEntityMapper.selectBatchIds(any())).thenReturn(List.of(
            buildResource(200L, 100L, 10L, 7),
            buildResource(201L, 100L, 10L, 7)
        ));
        when(abstractRoleMapper.selectBatchIds(any())).thenReturn(List.of(buildRole(30L, 100L, 10L)));

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setAbstractRoleId(30L);
        req.setPageNum(2);
        req.setPageSize(1);

        ConflictDetectionPageVo page = service.detectPage(req);

        assertEquals(2L, page.getTotal());
        assertEquals(1, page.getItems().size());
        assertEquals(30L, page.getItems().get(0).getAbstractRoleId());
    }

    @Test
    void detect_withoutRules_returnsEmptyPageAndSkipsEvent() {
        when(conflictRuleMapper.countPagedConflictViolations(100L, null, null)).thenReturn(0L);
        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);

        ConflictDetectionPageVo page = service.detectPage(req);

        assertTrue(page.getItems().isEmpty());
        assertEquals(0L, page.getTotal());
        verifyNoInteractions(roleResourcePermissionMapper, governanceEventPublisher);
    }

    @Test
    void detectPage_batchScan_usesPagedMapperResults() {
        ConflictViolationVo violation = new ConflictViolationVo();
        violation.setAbstractUserId(80L);
        violation.setResourceEntityId(200L);
        when(conflictRuleMapper.countPagedConflictViolations(100L, 10L, 200L)).thenReturn(3L);
        when(conflictRuleMapper.selectPagedConflictViolations(100L, 10L, 200L, 1L, 1L))
            .thenReturn(List.of(violation));

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);
        req.setBizDomainId(10L);
        req.setResourceEntityId(200L);
        req.setPageNum(2);
        req.setPageSize(1);

        ConflictDetectionPageVo page = service.detectPage(req);

        assertEquals(3L, page.getTotal());
        assertEquals(1, page.getItems().size());
        assertEquals(80L, page.getItems().get(0).getAbstractUserId());
        verify(governanceEventPublisher).publishConflictDetected(req, page.getItems(), 3L);
    }

    @Test
    void detect_compatEndpoint_returnsFullBatchResult() {
        ConflictViolationVo first = new ConflictViolationVo();
        first.setAbstractUserId(80L);
        ConflictViolationVo second = new ConflictViolationVo();
        second.setAbstractRoleId(30L);
        when(conflictRuleMapper.countPagedConflictViolations(100L, null, null)).thenReturn(2L);
        when(conflictRuleMapper.selectPagedConflictViolations(100L, null, null, 0L, 2L))
            .thenReturn(List.of(first, second));

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(100L);

        List<ConflictViolationVo> result = service.detect(req);

        assertEquals(2, result.size());
        verify(conflictRuleMapper).selectPagedConflictViolations(100L, null, null, 0L, 2L);
        verify(governanceEventPublisher).publishConflictDetected(req, result, 2L);
    }
}
