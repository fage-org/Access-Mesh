package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.ConditionListReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.ConditionUpdateReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.PermissionConditionVo;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionConditionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionConditionControllerTest {

    @Mock
    private PermissionConditionService permissionConditionService;

    @InjectMocks
    private PermissionConditionController controller;

    @Test
    void list_getDelegatesToService() {
        ConditionListReq req = new ConditionListReq();
        req.setTenantId(1L);
        PermissionConditionVo vo = new PermissionConditionVo();
        vo.setId(10L);
        when(permissionConditionService.list(req)).thenReturn(List.of(vo));

        R<List<PermissionConditionVo>> response = controller.list(req);

        assertEquals(1, response.getData().size());
        verify(permissionConditionService).list(req);
    }

    @Test
    void create_clearsBodyIdBeforeSave() {
        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(10L);
        req.setTenantId(1L);
        req.setCode("WORKDAY_ONLY");
        req.setName("Workday");
        req.setExpression("WORKDAY_ONLY");

        controller.create(req);

        ArgumentCaptor<ConditionSaveReq> captor = ArgumentCaptor.forClass(ConditionSaveReq.class);
        verify(permissionConditionService).save(captor.capture());
        assertNull(captor.getValue().getId());
    }

    @Test
    void update_usesPathConditionId() {
        ConditionUpdateReq req = new ConditionUpdateReq();
        req.setTenantId(1L);
        req.setEnabled(Boolean.FALSE);
        req.setReviewedBy(9L);

        controller.update(12L, req);

        ArgumentCaptor<ConditionUpdateReq> captor = ArgumentCaptor.forClass(ConditionUpdateReq.class);
        verify(permissionConditionService).update(org.mockito.ArgumentMatchers.eq(12L), captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getEnabled());
    }

    @Test
    void removeLegacy_delegates() {
        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of(1L, 2L));

        controller.removeLegacy(req);

        verify(permissionConditionService).remove(req);
    }

    @Test
    void update_nullPathThrowsInvalidRequest() {
        ConditionUpdateReq req = new ConditionUpdateReq();
        req.setTenantId(1L);

        assertThrows(PermissionServiceException.class, () -> controller.update(null, req));
    }
}
