package org.dromara.permission.service.impl;

import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionConditionServiceImplTest {

    @Mock
    private PcPermissionConditionMapper mapper;

    @InjectMocks
    private PermissionConditionServiceImpl service;

    @Test
    void save_newCustomCondition_defaultsToPending() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("level > 3");

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).insert(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_SOURCE_CUSTOM, entity.getConditionSource());
        assertEquals(PermissionConstants.CONDITION_STATUS_PENDING, entity.getStatus());
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
    }

    @Test
    void save_updateApprovedCondition_setsReviewFields() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("LEVEL_CHECK");
        req.setName("Level Check");
        req.setExpression("level > 3");
        req.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        req.setReviewedBy(99L);

        PcPermissionCondition existing = new PcPermissionCondition();
        existing.setId(10L);
        existing.setTenantId(1L);
        existing.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.save(req);

        ArgumentCaptor<PcPermissionCondition> captor = ArgumentCaptor.forClass(PcPermissionCondition.class);
        verify(mapper).updateById(captor.capture());
        PcPermissionCondition entity = captor.getValue();
        assertEquals(PermissionConstants.CONDITION_STATUS_APPROVED, entity.getStatus());
        assertEquals(99L, entity.getReviewedBy());
        assertNotNull(entity.getReviewedAt());
    }
}
