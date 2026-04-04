package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcTypeDefinition;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.TypeDefinitionListReq;
import org.dromara.permission.domain.dto.TypeDefinitionSaveReq;
import org.dromara.permission.mapper.PcTypeDefinitionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TypeDefinitionServiceImplTest {

    @Mock
    private PcTypeDefinitionMapper mapper;

    @InjectMocks
    private TypeDefinitionServiceImpl service;

    @Test
    void list_normal_returnsRows() {
        PcTypeDefinition entity = new PcTypeDefinition();
        entity.setId(1L);
        entity.setTenantId(1L);
        entity.setTypeKey("role_type");
        entity.setTypeValue(1);
        entity.setName("admin");
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        TypeDefinitionListReq req = new TypeDefinitionListReq();
        req.setTenantId(1L);
        req.setBizDomainId(10L);
        req.setTypeKey("role_type");

        assertEquals(1, service.list(req).size());
        assertEquals("role_type", service.list(req).get(0).getTypeKey());
    }

    @Test
    void save_insertNew_persistsEntity() {
        TypeDefinitionSaveReq req = new TypeDefinitionSaveReq();
        TypeDefinitionSaveReq.TypeDefinitionItem item = new TypeDefinitionSaveReq.TypeDefinitionItem();
        item.setTenantId(1L);
        item.setBizDomainId(10L);
        item.setTypeKey("resource_type");
        item.setTypeValue(2);
        item.setName("API");
        req.setItems(List.of(item));

        service.save(req);

        ArgumentCaptor<PcTypeDefinition> captor = ArgumentCaptor.forClass(PcTypeDefinition.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getTenantId());
        assertEquals(10L, captor.getValue().getBizDomainId());
        assertEquals("resource_type", captor.getValue().getTypeKey());
        assertEquals(PermissionConstants.NOT_DELETED, captor.getValue().getDeleteFlag());
    }

    @Test
    void save_updateExisting_tenantScopedLookup() {
        PcTypeDefinition existing = new PcTypeDefinition();
        existing.setId(5L);
        existing.setTenantId(1L);
        existing.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        TypeDefinitionSaveReq req = new TypeDefinitionSaveReq();
        TypeDefinitionSaveReq.TypeDefinitionItem item = new TypeDefinitionSaveReq.TypeDefinitionItem();
        item.setId(5L);
        item.setTenantId(1L);
        item.setBizDomainId(10L);
        item.setTypeKey("role_type");
        item.setTypeValue(3);
        item.setName("auditor");
        req.setItems(List.of(item));

        service.save(req);

        verify(mapper).updateById(existing);
        assertEquals(10L, existing.getBizDomainId());
        assertEquals(3, existing.getTypeValue());
    }

    @Test
    void remove_onlyCurrentTenantRowsDeleted() {
        PcTypeDefinition entity = new PcTypeDefinition();
        entity.setId(7L);
        entity.setTenantId(1L);
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        IdsReq req = new IdsReq();
        req.setTenantId(1L);
        req.setIds(List.of(7L));

        service.remove(req);

        verify(mapper).updateById(entity);
        assertEquals(7L, entity.getDeleteFlag());
        assertNotNull(entity.getDeletedAt());
    }

    @Test
    void remove_tenantIsolation_noRowsNoDelete() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        IdsReq req = new IdsReq();
        req.setTenantId(2L);
        req.setIds(List.of(7L));

        service.remove(req);

        verify(mapper, never()).updateById(any(PcTypeDefinition.class));
    }
}
