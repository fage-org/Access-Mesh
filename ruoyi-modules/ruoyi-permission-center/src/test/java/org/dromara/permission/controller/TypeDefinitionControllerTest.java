package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.SystemConfigListReq;
import org.dromara.permission.domain.dto.SystemConfigSaveReq;
import org.dromara.permission.domain.dto.TypeDefinitionListReq;
import org.dromara.permission.domain.dto.TypeDefinitionSaveReq;
import org.dromara.permission.domain.vo.TypeDefinitionVo;
import org.dromara.permission.service.TypeDefinitionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TypeDefinitionControllerTest {

    @Mock
    private TypeDefinitionService typeDefinitionService;

    @InjectMocks
    private TypeDefinitionController typeDefinitionController;

    @InjectMocks
    private SystemConfigController systemConfigController;

    @Test
    void typeDefinitionRemove_delegatesToService() {
        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of(1L));

        R<Void> result = typeDefinitionController.remove(req);

        verify(typeDefinitionService).remove(req);
        assertEquals(200, result.getCode());
    }

    @Test
    void systemConfigList_mapsTypeDefinitionResponse() {
        TypeDefinitionVo vo = new TypeDefinitionVo();
        vo.setId(1L);
        vo.setTenantId(1L);
        vo.setBizDomainId(10L);
        vo.setTypeKey("role_type");
        vo.setTypeValue(1);
        vo.setName("admin");
        when(typeDefinitionService.list(any(TypeDefinitionListReq.class))).thenReturn(List.of(vo));

        SystemConfigListReq req = new SystemConfigListReq();
        req.setTenantId(1L);
        req.setBizDomainId(10L);
        req.setConfigKey("role_type");

        R<?> result = systemConfigController.list(req);

        verify(typeDefinitionService).list(any(TypeDefinitionListReq.class));
        assertEquals(200, result.getCode());
    }

    @Test
    void systemConfigSave_mapsToTypeDefinitionSaveReq() {
        SystemConfigSaveReq req = new SystemConfigSaveReq();
        SystemConfigSaveReq.SystemConfigItem item = new SystemConfigSaveReq.SystemConfigItem();
        item.setTenantId(1L);
        item.setBizDomainId(10L);
        item.setConfigKey("resource_type");
        item.setTypeValue(2);
        item.setName("API");
        req.setItems(List.of(item));

        R<Void> result = systemConfigController.save(req);

        verify(typeDefinitionService).save(any(TypeDefinitionSaveReq.class));
        assertEquals(200, result.getCode());
    }

    @Test
    void systemConfigRemove_delegatesToService() {
        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of(2L));

        R<Void> result = systemConfigController.remove(req);

        verify(typeDefinitionService).remove(req);
        assertEquals(200, result.getCode());
    }
}
