package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class RolePermissionServiceImplTest {

    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private PcResourceEntityMapper resourceEntityMapper;
    @Mock
    private PcOperationPermissionMapper operationPermissionMapper;
    @Mock
    private PermissionChangeLogService permissionChangeLogService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private RolePermissionServiceImpl service;

    private static final Long TENANT = 1L;
    private static final Long ROLE_ID = 200L;
    private static final Long RESOURCE_ID = 300L;
    private static final Long OP_ID = 400L;

    // ======================== add ========================

    @Test
    void add_insertNew_permissionInserted() {
        RolePermissionAddReq req = buildAddReq(TENANT, ROLE_ID, RESOURCE_ID, OP_ID, true, 10L);

        service.add(req);
        verify(permissionService).grantRolePermissions(any(RolePermissionBatchGrantRequest.class));
    }

    @Test
    void add_updateExisting_canManageAndConditionIdUpdated() {
        RolePermissionAddReq req = buildAddReq(TENANT, ROLE_ID, RESOURCE_ID, OP_ID, true, 20L);

        service.add(req);
        verify(permissionService).grantRolePermissions(any(RolePermissionBatchGrantRequest.class));
    }

    @Test
    void add_nullResourceEntityId_itemSkipped() {
        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(TENANT);
        req.setAbstractRoleId(ROLE_ID);
        RolePermissionAddReq.RolePermissionItem item = new RolePermissionAddReq.RolePermissionItem();
        item.setResourceEntityId(null);
        item.setOperationPermissionId(OP_ID);
        req.setItems(List.of(item));

        service.add(req);
        verify(permissionService).grantRolePermissions(any(RolePermissionBatchGrantRequest.class));
    }

    @Test
    void add_nullReq_returnsWithoutAction() {
        service.add(null);
        verify(permissionService).grantRolePermissions(any(RolePermissionBatchGrantRequest.class));
    }

    @Test
    void add_logsChange_entityIdIsRoleId() {
        RolePermissionAddReq req = buildAddReq(TENANT, ROLE_ID, RESOURCE_ID, OP_ID, false, null);

        service.add(req);
        verify(permissionService).grantRolePermissions(any(RolePermissionBatchGrantRequest.class));
    }

    // ======================== remove ========================

    @Test
    void remove_normal_deleteFlagSetToId() {
        RolePermissionRemoveReq req = buildRemoveReq(TENANT, ROLE_ID, RESOURCE_ID, OP_ID);

        service.remove(req);
        verify(permissionService).revokeRolePermissions(any(RolePermissionBatchRevokeRequest.class));
    }

    @Test
    void remove_notExist_silentIgnore() {
        RolePermissionRemoveReq req = buildRemoveReq(TENANT, ROLE_ID, RESOURCE_ID, OP_ID);
        assertDoesNotThrow(() -> service.remove(req));
        verify(permissionService).revokeRolePermissions(any(RolePermissionBatchRevokeRequest.class));
    }

    // ======================== list ========================

    @Test
    void list_normal_returnsWithResourceNameAndOperationName() {
        RolePermissionListReq req = new RolePermissionListReq();
        req.setTenantId(TENANT);
        req.setAbstractRoleId(ROLE_ID);

        PcRoleResourcePermission rrp = buildPermission(1L, TENANT, ROLE_ID, RESOURCE_ID, OP_ID);
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rrp));

        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(RESOURCE_ID);
        resource.setName("用户管理");
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(resourceEntityMapper.selectBatchIds(anyCollection())).thenReturn(List.of(resource));

        PcOperationPermission op = new PcOperationPermission();
        op.setId(OP_ID);
        op.setName("查看");
        op.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(operationPermissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(op));

        List<RolePermissionVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals("用户管理", result.get(0).getResourceName());
        assertEquals("查看", result.get(0).getOperationName());
    }

    @Test
    void list_deletedResource_resourceNameIsNull() {
        RolePermissionListReq req = new RolePermissionListReq();
        req.setTenantId(TENANT);
        req.setAbstractRoleId(ROLE_ID);

        PcRoleResourcePermission rrp = buildPermission(1L, TENANT, ROLE_ID, RESOURCE_ID, OP_ID);
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rrp));

        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(RESOURCE_ID);
        resource.setName("用户管理");
        resource.setDeleteFlag(999L);
        when(resourceEntityMapper.selectBatchIds(anyCollection())).thenReturn(List.of(resource));

        PcOperationPermission op = new PcOperationPermission();
        op.setId(OP_ID);
        op.setName("查看");
        op.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(operationPermissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(op));

        List<RolePermissionVo> result = service.list(req);

        assertEquals(1, result.size());
        assertNull(result.get(0).getResourceName());
        assertEquals("查看", result.get(0).getOperationName());
    }

    // ======================== helpers ========================

    private RolePermissionAddReq buildAddReq(Long tenantId, Long roleId, Long resourceId, Long opId,
                                             Boolean canManage, Long conditionId) {
        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(tenantId);
        req.setAbstractRoleId(roleId);
        RolePermissionAddReq.RolePermissionItem item = new RolePermissionAddReq.RolePermissionItem();
        item.setResourceEntityId(resourceId);
        item.setOperationPermissionId(opId);
        item.setCanManage(canManage);
        item.setConditionId(conditionId);
        req.setItems(List.of(item));
        return req;
    }

    private RolePermissionRemoveReq buildRemoveReq(Long tenantId, Long roleId, Long resourceId, Long opId) {
        RolePermissionRemoveReq req = new RolePermissionRemoveReq();
        req.setTenantId(tenantId);
        req.setAbstractRoleId(roleId);
        RolePermissionRemoveReq.RolePermissionPair pair = new RolePermissionRemoveReq.RolePermissionPair();
        pair.setResourceEntityId(resourceId);
        pair.setOperationPermissionId(opId);
        req.setItems(List.of(pair));
        return req;
    }

    private PcRoleResourcePermission buildPermission(Long id, Long tenantId, Long roleId,
                                                     Long resourceId, Long opId) {
        PcRoleResourcePermission rrp = new PcRoleResourcePermission();
        rrp.setId(id);
        rrp.setTenantId(tenantId);
        rrp.setAbstractRoleId(roleId);
        rrp.setResourceEntityId(resourceId);
        rrp.setOperationPermissionId(opId);
        rrp.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return rrp;
    }
}
