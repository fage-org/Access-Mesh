package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.sync.SyncContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingSyncHandlerImplTest {

    @Mock private ResourceApiMappingMapper resourceApiMappingMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;

    private MappingSyncHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new MappingSyncHandlerImpl(resourceApiMappingMapper, resourceEntityMapper);
    }

    @Test
    void shouldThrowSystemExceptionWhenSyncedResourceWasNotCreated() {
        ServiceConfigSyncReq req = new ServiceConfigSyncReq(
            "svc-a",
            "/base",
            "FULL",
            List.of(new ServiceConfigSyncReq.GroupItem(
                "default",
                "默认",
                List.of(new ServiceConfigSyncReq.ApiItem("demo", "GET", "/demo", "READ", "demo:read", "demo api"))
            ))
        );
        SyncContext context = SyncContext.of(1L, new ServiceConfig(), req, 100L, "/base", 1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(
            eq(1L), eq(1), eq("demo:read"), eq(PermConstants.CodeType.DEFAULT)
        )).thenReturn(null);

        SystemException exception = assertThrows(SystemException.class, () -> handler.syncMappings(context));

        assertEquals(PermissionErrorCode.SYNC_RESOURCE_NOT_FOUND.getCode(), exception.getErrorCode());
    }
}