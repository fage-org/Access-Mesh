package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.vo.PermissionGrantVo;
import org.dromara.permission.domain.vo.PermissionRevokeVo;
import org.dromara.permission.domain.vo.PermissionSnapshotVo;
import org.dromara.permission.domain.vo.PermissionVersionVo;
import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.GrantResult;
import org.dromara.permission.model.permission.PermissionSnapshot;
import org.dromara.permission.model.permission.PermissionVersionQueryRequest;
import org.dromara.permission.model.permission.PermissionVersionResult;
import org.dromara.permission.model.permission.RevokePermissionRequest;
import org.dromara.permission.model.permission.RevokeResult;
import org.dromara.permission.model.permission.SnapshotEntry;
import org.dromara.permission.model.permission.SnapshotRequest;
import org.dromara.permission.service.PermissionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionServiceControllerTest {

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private PermissionServiceController controller;

    @Test
    void grant_delegates() {
        GrantPermissionRequest request = new GrantPermissionRequest();
        GrantResult result = GrantResult.success(1L);
        when(permissionService.grant(request)).thenReturn(result);

        R<PermissionGrantVo> response = controller.grant(request);

        verify(permissionService).grant(request);
        assertEquals(1L, response.getData().getPermissionId());
    }

    @Test
    void revoke_delegates() {
        RevokePermissionRequest request = new RevokePermissionRequest();
        RevokeResult result = RevokeResult.success(2L);
        when(permissionService.revoke(request)).thenReturn(result);

        R<PermissionRevokeVo> response = controller.revoke(request);

        verify(permissionService).revoke(request);
        assertEquals(2L, response.getData().getPermissionId());
    }

    @Test
    void snapshot_delegates() {
        SnapshotRequest request = new SnapshotRequest();
        PermissionSnapshot snapshot = new PermissionSnapshot();
        snapshot.setVersionToken("1-v1");
        SnapshotEntry entry = new SnapshotEntry();
        entry.setResourceType(2);
        entry.setServiceCode("system-service");
        entry.setHttpMethod("GET");
        entry.setPathPattern("/api/system/user/list");
        snapshot.setEntries(java.util.List.of(entry));
        when(permissionService.buildSnapshot(request)).thenReturn(snapshot);

        R<PermissionSnapshotVo> response = controller.snapshot(request);

        verify(permissionService).buildSnapshot(request);
        assertEquals("1-v1", response.getData().getVersionToken());
        assertEquals(2, response.getData().getEntries().get(0).getResourceType());
        assertEquals("system-service", response.getData().getEntries().get(0).getServiceCode());
    }

    @Test
    void version_delegates() {
        PermissionVersionQueryRequest request = new PermissionVersionQueryRequest();
        PermissionVersionResult result = new PermissionVersionResult();
        result.setVersionToken("1-v2");
        when(permissionService.queryVersion(request)).thenReturn(result);

        R<PermissionVersionVo> response = controller.version(request);

        verify(permissionService).queryVersion(request);
        assertEquals("1-v2", response.getData().getVersionToken());
    }
}
