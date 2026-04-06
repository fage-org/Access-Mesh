package org.dromara.permission.service.impl;

import org.dromara.permission.condition.PermissionConditionExpressionEvaluator;
import org.dromara.permission.condition.PermissionConditionPresetHandlerRegistry;
import org.dromara.permission.condition.builtin.InternalIpConditionHandler;
import org.dromara.permission.condition.builtin.WorkdayOnlyConditionHandler;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.dto.ConditionListReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.ConditionUpdateReq;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionConditionServiceImplTest {

    @Mock
    private PcPermissionConditionMapper mapper;

    private PermissionConditionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionConditionServiceImpl(
            mapper,
            new PermissionConditionPresetHandlerRegistry(List.of(
                new WorkdayOnlyConditionHandler(),
                new InternalIpConditionHandler()
            )),
            new PermissionConditionExpressionEvaluator()
        );
    }

    @Test
    void save_newCustomCondition_defaultsToPending() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] > 3");

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).insert(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_SOURCE_CUSTOM, entity.getConditionSource());
        assertEquals(PermissionConstants.CONDITION_STATUS_PENDING, entity.getStatus());
        assertEquals(Boolean.TRUE, entity.getEnabled());
        assertEquals(PermissionConstants.NOT_DELETED, entity.getDeleteFlag());
    }

    @Test
    void save_newPresetCondition_forcesApproved() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setTenantId(1L);
        req.setCode("WORKDAY_ONLY");
        req.setName("Workday");
        req.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        req.setExpression("WORKDAY_ONLY");

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).insert(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_APPROVED, entity.getStatus());
        assertEquals("WORKDAY_ONLY", entity.getExpression());
    }

    @Test
    void save_updateApprovedCondition_setsReviewFields() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] > 3");
        req.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        req.setReviewedBy(99L);

        PcPermissionCondition existing = baseExistingCondition();
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_APPROVED, entity.getStatus());
        assertEquals(99L, entity.getReviewedBy());
        assertNotNull(entity.getReviewedAt());
    }

    @Test
    void save_customConditionDefinitionChanged_resetsToPending() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] >= 5");

        PcPermissionCondition existing = baseExistingCondition();
        existing.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        existing.setReviewedBy(88L);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_PENDING, entity.getStatus());
        assertNull(entity.getReviewedBy());
    }

    @Test
    void save_toggleEnabled_preservesApprovedStatus() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] > 3");
        req.setEnabled(Boolean.FALSE);

        PcPermissionCondition existing = baseExistingCondition();
        existing.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        existing.setEnabled(Boolean.TRUE);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_APPROVED, entity.getStatus());
        assertEquals(Boolean.FALSE, entity.getEnabled());
    }

    @Test
    void save_toggleEnabled_preservesExistingReviewMetadata() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] > 3");
        req.setEnabled(Boolean.FALSE);

        LocalDateTime reviewedAt = LocalDateTime.of(2026, 4, 1, 9, 0);
        PcPermissionCondition existing = baseExistingCondition();
        existing.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        existing.setEnabled(Boolean.TRUE);
        existing.setReviewedBy(88L);
        existing.setReviewedAt(reviewedAt);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(88L, entity.getReviewedBy());
        assertEquals(reviewedAt, entity.getReviewedAt());
    }

    @Test
    void update_partialEnableOnly_mergesExistingDefinition() {
        ConditionUpdateReq req = new ConditionUpdateReq();
        req.setTenantId(1L);
        req.setEnabled(Boolean.FALSE);

        PcPermissionCondition existing = baseExistingCondition();
        existing.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        when(mapper.selectOne(any())).thenReturn(existing, existing);

        service.update(10L, req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals("LEVEL_CHECK", entity.getCode());
        assertEquals("Level Check", entity.getName());
        assertEquals("['level'] > 3", entity.getExpression());
        assertEquals(Boolean.FALSE, entity.getEnabled());
    }

    @Test
    void update_partialReviewOnly_setsReviewMetadata() {
        ConditionUpdateReq req = new ConditionUpdateReq();
        req.setTenantId(1L);
        req.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        req.setReviewedBy(99L);

        PcPermissionCondition existing = baseExistingCondition();
        existing.setStatus(PermissionConstants.CONDITION_STATUS_PENDING);
        when(mapper.selectOne(any())).thenReturn(existing, existing);

        service.update(10L, req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_APPROVED, entity.getStatus());
        assertEquals(99L, entity.getReviewedBy());
        assertNotNull(entity.getReviewedAt());
    }

    @Test
    void save_customConditionDefinitionChanged_ignoresExplicitApprovedStatus() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] >= 5");
        req.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        req.setReviewedBy(99L);

        PcPermissionCondition existing = baseExistingCondition();
        existing.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        existing.setReviewedBy(88L);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_PENDING, entity.getStatus());
        assertNull(entity.getReviewedBy());
    }

    @Test
    void save_unknownPresetHandler_throwsInvalidRequest() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setTenantId(1L);
        req.setCode("UNKNOWN");
        req.setName("Unknown");
        req.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        req.setExpression("UNKNOWN");

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.save(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void save_updatePresetWithoutExpression_preservesExistingHandlerCode() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(20L);
        req.setTenantId(1L);
        req.setCode("INTERNAL_NETWORK_ONLY");
        req.setName("Internal network only");
        req.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        req.setEnabled(Boolean.FALSE);

        PcPermissionCondition existing = new PcPermissionCondition();
        existing.setId(20L);
        existing.setTenantId(1L);
        existing.setCode("INTERNAL_NETWORK_ONLY");
        existing.setName("Internal network only");
        existing.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        existing.setExpression("INTERNAL_IP");
        existing.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        existing.setEnabled(Boolean.TRUE);
        existing.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals("INTERNAL_IP", entity.getExpression());
        assertEquals(Boolean.FALSE, entity.getEnabled());
        assertEquals(PermissionConstants.CONDITION_STATUS_APPROVED, entity.getStatus());
    }

    @Test
    void save_invalidCustomExpression_throwsInvalidRequest() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setTenantId(1L);
        req.setCode("BROKEN");
        req.setName("Broken");
        req.setExpression("['broken'");

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.save(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void list_filtersByConditionSourceAndStatus() {
        ConditionListReq req = new ConditionListReq();
        req.setTenantId(1L);
        req.setConditionSource(PermissionConstants.CONDITION_SOURCE_CUSTOM.toLowerCase());
        req.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED.toLowerCase());

        when(mapper.selectList(any())).thenReturn(List.of(baseExistingCondition()));

        List<?> result = service.list(req);

        assertEquals(1, result.size());
        verify(mapper).selectList(any());
    }

    @Test
    void list_presetWithPendingStatus_returnsEmptyWithoutQuery() {
        ConditionListReq req = new ConditionListReq();
        req.setTenantId(1L);
        req.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        req.setStatus(PermissionConstants.CONDITION_STATUS_PENDING);

        List<?> result = service.list(req);

        assertEquals(0, result.size());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void list_missingTenantId_throwsInvalidRequest() {
        ConditionListReq req = new ConditionListReq();

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.list(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void save_updateMissingCondition_throwsInvalidRequest() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] > 3");
        when(mapper.selectOne(any())).thenReturn(null);

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.save(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void save_missingTenantId_throwsInvalidRequest() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("['level'] > 3");

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.save(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void update_missingTenantId_throwsInvalidRequest() {
        ConditionUpdateReq req = new ConditionUpdateReq();

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.update(10L, req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void remove_missingCondition_throwsInvalidRequest() {
        org.dromara.permission.domain.dto.IdsReq req = new org.dromara.permission.domain.dto.IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of(10L, 11L));
        when(mapper.selectOne(any())).thenReturn(baseExistingCondition()).thenReturn((PcPermissionCondition) null);

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.remove(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void remove_emptyIds_throwsInvalidRequest() {
        org.dromara.permission.domain.dto.IdsReq req = new org.dromara.permission.domain.dto.IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of());

        PermissionServiceException ex = assertThrows(PermissionServiceException.class, () -> service.remove(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    private PcPermissionCondition baseExistingCondition() {
        PcPermissionCondition existing = new PcPermissionCondition();
        existing.setId(10L);
        existing.setTenantId(1L);
        existing.setCode("LEVEL_CHECK");
        existing.setName("Level Check");
        existing.setExpression("['level'] > 3");
        existing.setConditionSource(PermissionConstants.CONDITION_SOURCE_CUSTOM);
        existing.setEnabled(Boolean.TRUE);
        existing.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return existing;
    }
}
