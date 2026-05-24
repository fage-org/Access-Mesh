package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类型定义应用服务测试类
 * <p>
 * 测试TypeDefinitionAppServiceImpl的各项功能。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TypeDefinitionAppServiceImplTest {

    @Mock private TypeDefinitionMapper typeDefinitionMapper;
    @Mock private PermQueryEngine engine;

    private TypeDefinitionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TypeDefinitionAppServiceImpl(
            typeDefinitionMapper, engine
        );
    }

    @Test
    void shouldCreateTypeWhenPermissionGranted() {
        when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
            .thenReturn(true);

        TypeCreateReq req = new TypeCreateReq(
            "resource_type", 100, "TestType", "A test type", false, 0, null
        );

        TypeDefinitionResp result = service.createType(1L, req, 100L);

        ArgumentCaptor<TypeDefinition> captor = ArgumentCaptor.forClass(TypeDefinition.class);
        verify(typeDefinitionMapper).insert(captor.capture());
        TypeDefinition inserted = captor.getValue();

        assertNotNull(result);
        assertEquals("TestType", result.name());
        assertEquals("resource_type", inserted.getTypeKey());
        assertEquals(100, inserted.getTypeValue());
        assertEquals(100L, inserted.getCreatedBy());
    }

    @Test
    void shouldThrowWhenCreateTypePermissionDenied() {
        when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
            .thenReturn(false);

        TypeCreateReq req = new TypeCreateReq(
            "resource_type", 100, "TestType", "A test type", false, 0, null
        );

        assertThrows(SecurityException.class, () -> service.createType(1L, req, 100L));
    }

    @Test
    void shouldThrowTypeDefinitionNotFoundWhenUpdateTargetMissing() {
        when(engine.hasPermission(eq(1L), eq(100L), any(), eq(99L), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectValidById(1L, 99L)).thenReturn(null);

        TypeUpdateReq req = new TypeUpdateReq(99L, "Updated", "desc", 1, null);

        BizException exception = assertThrows(BizException.class, () -> service.updateType(1L, req, 100L));

        assertEquals(PermissionErrorCode.TYPE_DEFINITION_NOT_FOUND.getCode(), exception.getErrorCode());
    }
}
