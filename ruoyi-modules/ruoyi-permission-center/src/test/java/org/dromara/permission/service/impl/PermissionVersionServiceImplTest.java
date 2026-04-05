package org.dromara.permission.service.impl;

import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.mapper.PcPermissionVersionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionVersionServiceImplTest {

    @Mock
    private PcPermissionVersionMapper mapper;

    @InjectMocks
    private PermissionVersionServiceImpl service;

    @Test
    void queryCurrentVersion_withoutHistory_returnsZeroVersion() {
        when(mapper.selectLatestByTenant(1L)).thenReturn(null);

        PcPermissionVersion current = service.queryCurrentVersion(1L);

        assertNotNull(current);
        assertEquals(1L, current.getTenantId());
        assertEquals(0L, current.getVersionNo());
        assertNotNull(current.getUpdatedAt());
    }

    @Test
    void bumpVersion_usesLatestVersionAndPersistsTriggerInfo() {
        PcPermissionVersion latest = new PcPermissionVersion();
        latest.setTenantId(1L);
        latest.setVersionNo(4L);
        latest.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectLatestByTenant(1L)).thenReturn(latest);
        when(mapper.insert(any(PcPermissionVersion.class))).thenReturn(1);

        PcPermissionVersion current = service.bumpVersion(1L, "user_role", 99L, "grant-role");

        verify(mapper).lockTenantVersion(1L);
        ArgumentCaptor<PcPermissionVersion> captor = ArgumentCaptor.forClass(PcPermissionVersion.class);
        verify(mapper).insert(captor.capture());
        PcPermissionVersion inserted = captor.getValue();
        assertEquals(1L, inserted.getTenantId());
        assertEquals(5L, inserted.getVersionNo());
        assertEquals("user_role", inserted.getTriggerEntityType());
        assertEquals(99L, inserted.getTriggerEntityId());
        assertEquals("grant-role", inserted.getRemark());
        assertNotNull(inserted.getCreatedAt());
        assertNotNull(inserted.getUpdatedAt());
        assertEquals(5L, current.getVersionNo());
    }

    @Test
    void buildVersionToken_formatsTenantAndVersionNumber() {
        assertEquals("1-v7", service.buildVersionToken(1L, 7L));
    }
}
