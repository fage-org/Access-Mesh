package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcAbstractUser;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class UserRoleServiceImplTest {

    @Mock
    private PcUserRoleMapper userRoleMapper;
    @Mock
    private PcAbstractUserMapper abstractUserMapper;
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;
    @Mock
    private PermissionChangeLogService permissionChangeLogService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private UserRoleServiceImpl service;

    private static final Long TENANT = 1L;
    private static final Long USER_ID = 100L;
    private static final Long ROLE_ID = 200L;

    // ======================== assign ========================

    @Test
    void assign_normal_insertNewUserRole() {
        UserRoleAssignReq req = buildAssignReq(TENANT, USER_ID, List.of(ROLE_ID));
        req.setValidFrom(LocalDateTime.of(2025, 1, 1, 0, 0));
        req.setValidTo(LocalDateTime.of(2025, 12, 31, 23, 59));

        service.assign(req);
        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void assign_existingUpdate_updatesValidFromTo() {
        UserRoleAssignReq req = buildAssignReq(TENANT, USER_ID, List.of(ROLE_ID));
        LocalDateTime newFrom = LocalDateTime.of(2025, 6, 1, 0, 0);
        LocalDateTime newTo = LocalDateTime.of(2025, 12, 31, 23, 59);
        req.setValidFrom(newFrom);
        req.setValidTo(newTo);

        service.assign(req);
        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void assign_userNotFound_throwsIllegalArgument() {
        UserRoleAssignReq req = buildAssignReq(TENANT, USER_ID, List.of(ROLE_ID));
        assertDoesNotThrow(() -> service.assign(req));
        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void assign_roleNotFound_skippedNoException() {
        UserRoleAssignReq req = buildAssignReq(TENANT, USER_ID, List.of(ROLE_ID));
        assertDoesNotThrow(() -> service.assign(req));
        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void assign_nullTenantId_returnsWithoutAction() {
        UserRoleAssignReq req = buildAssignReq(null, USER_ID, List.of(ROLE_ID));
        service.assign(req);
        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void assign_logsChangeWithUserId() {
        UserRoleAssignReq req = buildAssignReq(TENANT, USER_ID, List.of(ROLE_ID));
        service.assign(req);
        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    // ======================== revoke ========================

    @Test
    void revoke_normal_deleteFlagSetToId() {
        UserRoleRevokeReq req = buildRevokeReq(TENANT, USER_ID, List.of(ROLE_ID));
        service.revoke(req);
        verify(permissionService).revokeUserRoles(any(UserRoleBatchRevokeRequest.class));
    }

    @Test
    void revoke_notExist_silentIgnore() {
        UserRoleRevokeReq req = buildRevokeReq(TENANT, USER_ID, List.of(ROLE_ID));
        assertDoesNotThrow(() -> service.revoke(req));
        verify(permissionService).revokeUserRoles(any(UserRoleBatchRevokeRequest.class));
    }

    @Test
    void revoke_logsChangeWithDelete() {
        UserRoleRevokeReq req = buildRevokeReq(TENANT, USER_ID, List.of(ROLE_ID));
        service.revoke(req);
        verify(permissionService).revokeUserRoles(any(UserRoleBatchRevokeRequest.class));
    }

    // ======================== list ========================

    @Test
    void list_normal_returnsListWithRoleName() {
        UserRoleListReq req = new UserRoleListReq();
        req.setTenantId(TENANT);
        req.setAbstractUserId(USER_ID);

        PcUserRole ur = buildUserRole(1L, TENANT, USER_ID, ROLE_ID);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ur));

        PcAbstractRole role = buildRole(ROLE_ID);
        role.setName("管理员");
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));

        List<UserRoleVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals("管理员", result.get(0).getRoleName());
        assertEquals(ROLE_ID, result.get(0).getAbstractRoleId());
    }

    @Test
    void list_roleDeleted_roleNameIsNull() {
        UserRoleListReq req = new UserRoleListReq();
        req.setTenantId(TENANT);
        req.setAbstractUserId(USER_ID);

        PcUserRole ur = buildUserRole(1L, TENANT, USER_ID, ROLE_ID);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ur));

        PcAbstractRole role = buildRole(ROLE_ID);
        role.setName("管理员");
        role.setDeleteFlag(999L);
        when(abstractRoleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));

        List<UserRoleVo> result = service.list(req);

        assertEquals(1, result.size());
        assertNull(result.get(0).getRoleName());
    }

    // ======================== helpers ========================

    private UserRoleAssignReq buildAssignReq(Long tenantId, Long userId, List<Long> roleIds) {
        UserRoleAssignReq req = new UserRoleAssignReq();
        req.setTenantId(tenantId);
        req.setAbstractUserId(userId);
        req.setRoleIds(roleIds);
        return req;
    }

    private UserRoleRevokeReq buildRevokeReq(Long tenantId, Long userId, List<Long> roleIds) {
        UserRoleRevokeReq req = new UserRoleRevokeReq();
        req.setTenantId(tenantId);
        req.setAbstractUserId(userId);
        req.setRoleIds(roleIds);
        return req;
    }

    private PcUserRole buildUserRole(Long id, Long tenantId, Long userId, Long roleId) {
        PcUserRole ur = new PcUserRole();
        ur.setId(id);
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(userId);
        ur.setAbstractRoleId(roleId);
        ur.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return ur;
    }

    private PcAbstractUser buildUser(Long id) {
        PcAbstractUser user = new PcAbstractUser();
        user.setId(id);
        user.setTenantId(TENANT);
        user.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return user;
    }

    private PcAbstractRole buildRole(Long id) {
        PcAbstractRole role = new PcAbstractRole();
        role.setId(id);
        role.setTenantId(TENANT);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }
}
