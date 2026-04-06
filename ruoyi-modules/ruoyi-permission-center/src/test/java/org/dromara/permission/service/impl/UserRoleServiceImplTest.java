package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class UserRoleServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long USER_ID = 100L;
    private static final Long ROLE_ID = 200L;

    @Mock
    private PcUserRoleMapper userRoleMapper;
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;

    @InjectMocks
    private UserRoleServiceImpl service;

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

    private PcUserRole buildUserRole(Long id, Long tenantId, Long userId, Long roleId) {
        PcUserRole ur = new PcUserRole();
        ur.setId(id);
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(userId);
        ur.setAbstractRoleId(roleId);
        ur.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return ur;
    }

    private PcAbstractRole buildRole(Long id) {
        PcAbstractRole role = new PcAbstractRole();
        role.setId(id);
        role.setTenantId(TENANT);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }
}
