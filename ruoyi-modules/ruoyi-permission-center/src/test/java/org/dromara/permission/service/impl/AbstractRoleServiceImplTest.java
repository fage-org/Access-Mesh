package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.RoleListReq;
import org.dromara.permission.domain.dto.RoleSaveReq;
import org.dromara.permission.domain.vo.AbstractRoleVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.service.support.PermissionTreePathManager;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class AbstractRoleServiceImplTest {

    @Mock
    private PcAbstractRoleMapper mapper;
    @Mock
    private PcUserRoleMapper userRoleMapper;
    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private TypeDefinitionReader typeDefinitionReader;
    @Mock
    private PermissionTreePathManager treePathManager;

    @InjectMocks
    private AbstractRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            String parentPath = invocation.getArgument(0);
            Long id = invocation.getArgument(1);
            return parentPath == null ? "/" + id : parentPath + "/" + id;
        }).when(treePathManager).buildPath(any(), any());
    }

    private PcAbstractRole buildRole(Long id, Long tenantId, Long bizDomainId,
                                      Long parentId, String name, String path, Integer sortOrder) {
        PcAbstractRole r = new PcAbstractRole();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setBizDomainId(bizDomainId);
        r.setParentId(parentId);
        r.setName(name);
        r.setPath(path);
        r.setSortOrder(sortOrder);
        r.setExtra("{}");
        r.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return r;
    }

    // ── list ──

    @Test
    void list_nullParentId_returnsTree() {
        PcAbstractRole root = buildRole(1L, 100L, null, null, "root", "/1", 0);
        PcAbstractRole child = buildRole(2L, 100L, null, 1L, "child", "/1/2", 0);

        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(root, child));

        RoleListReq req = new RoleListReq();
        req.setTenantId(100L);

        List<AbstractRoleVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals("root", result.get(0).getName());
        assertNotNull(result.get(0).getChildren());
        assertEquals(1, result.get(0).getChildren().size());
        assertEquals("child", result.get(0).getChildren().get(0).getName());
    }

    @Test
    void list_withParentId_returnsFlatList() {
        PcAbstractRole child1 = buildRole(2L, 100L, null, 1L, "c1", "/1/2", 0);
        PcAbstractRole child2 = buildRole(3L, 100L, null, 1L, "c2", "/1/3", 1);

        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(child1, child2));

        RoleListReq req = new RoleListReq();
        req.setTenantId(100L);
        req.setParentId(1L);

        List<AbstractRoleVo> result = service.list(req);

        assertEquals(2, result.size());
        assertEquals("c1", result.get(0).getName());
        assertEquals("c2", result.get(1).getName());
    }

    @Test
    void list_nullTenantId_returnsEmpty() {
        RoleListReq req = new RoleListReq();
        List<AbstractRoleVo> result = service.list(req);
        assertTrue(result.isEmpty());
        verifyNoInteractions(mapper);
    }

    @Test
    void list_threeLayerTree() {
        PcAbstractRole root = buildRole(1L, 100L, null, null, "root", "/1", 0);
        PcAbstractRole child = buildRole(2L, 100L, null, 1L, "child", "/1/2", 0);
        PcAbstractRole grandchild = buildRole(3L, 100L, null, 2L, "grandchild", "/1/2/3", 0);

        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(root, child, grandchild));

        RoleListReq req = new RoleListReq();
        req.setTenantId(100L);

        List<AbstractRoleVo> result = service.list(req);

        assertEquals(1, result.size());
        AbstractRoleVo rootVo = result.get(0);
        assertEquals(1, rootVo.getChildren().size());
        AbstractRoleVo childVo = rootVo.getChildren().get(0);
        assertEquals(1, childVo.getChildren().size());
        assertEquals("grandchild", childVo.getChildren().get(0).getName());
    }

    @Test
    void list_bizDomainIdFilter() {
        PcAbstractRole role = buildRole(1L, 100L, 10L, null, "filtered", "/1", 0);

        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(role));

        RoleListReq req = new RoleListReq();
        req.setTenantId(100L);
        req.setBizDomainId(10L);

        List<AbstractRoleVo> result = service.list(req);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getBizDomainId());
    }

    // ── save ──

    @Test
    void save_newRootRole() {
        doAnswer(invocation -> {
            PcAbstractRole entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(mapper).insert(any(PcAbstractRole.class));

        RoleSaveReq req = new RoleSaveReq();
        req.setTenantId(100L);
        req.setName("Admin");
        req.setRoleType(1);
        req.setSortOrder(0);

        service.save(req);

        ArgumentCaptor<PcAbstractRole> captor = ArgumentCaptor.forClass(PcAbstractRole.class);
        verify(mapper).insert(captor.capture());
        verify(mapper).updateById(captor.capture());

        PcAbstractRole updated = captor.getAllValues().get(1);
        assertEquals("/1", updated.getPath());
        assertNull(updated.getParentId());
    }

    @Test
    void save_newChildRole() {
        PcAbstractRole parent = buildRole(10L, 100L, null, null, "parent", "/10", 0);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(parent);

        doAnswer(invocation -> {
            PcAbstractRole entity = invocation.getArgument(0);
            entity.setId(20L);
            return 1;
        }).when(mapper).insert(any(PcAbstractRole.class));

        RoleSaveReq req = new RoleSaveReq();
        req.setTenantId(100L);
        req.setParentId(10L);
        req.setName("Child");
        req.setRoleType(1);

        service.save(req);

        ArgumentCaptor<PcAbstractRole> captor = ArgumentCaptor.forClass(PcAbstractRole.class);
        verify(mapper).insert(captor.capture());
        verify(mapper).updateById(captor.capture());

        PcAbstractRole updated = captor.getAllValues().get(1);
        assertEquals("/10/20", updated.getPath());
        assertEquals(10L, updated.getParentId());
    }

    @Test
    void save_updateExisting() {
        PcAbstractRole existing = buildRole(5L, 100L, null, null, "OldName", "/5", 0);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        RoleSaveReq req = new RoleSaveReq();
        req.setId(5L);
        req.setTenantId(100L);
        req.setName("NewName");
        req.setRoleType(2);
        req.setSortOrder(3);
        req.setExtra("{\"key\":\"val\"}");

        service.save(req);

        verify(mapper).updateById(existing);
        assertEquals("NewName", existing.getName());
        assertEquals(2, existing.getRoleType());
        assertEquals(3, existing.getSortOrder());
        assertEquals("{\"key\":\"val\"}", existing.getExtra());
        verify(mapper, never()).insert(any(PcAbstractRole.class));
    }

    @Test
    void save_parentIdZero() {
        doAnswer(invocation -> {
            PcAbstractRole entity = invocation.getArgument(0);
            entity.setId(7L);
            return 1;
        }).when(mapper).insert(any(PcAbstractRole.class));

        RoleSaveReq req = new RoleSaveReq();
        req.setTenantId(100L);
        req.setParentId(0L);
        req.setName("ZeroParent");
        req.setRoleType(1);

        service.save(req);

        ArgumentCaptor<PcAbstractRole> captor = ArgumentCaptor.forClass(PcAbstractRole.class);
        verify(mapper).insert(captor.capture());
        assertNull(captor.getValue().getParentId());

        verify(mapper).updateById(captor.capture());
        assertEquals("/7", captor.getAllValues().get(1).getPath());
    }

    @Test
    void save_nullTenantId() {
        RoleSaveReq req = new RoleSaveReq();
        req.setName("NoTenant");

        service.save(req);

        verifyNoInteractions(mapper);
    }

    // ── remove ──

    @Test
    void remove_normal() {
        PcAbstractRole entity = buildRole(5L, 100L, null, null, "r", "/5", 0);
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(entity));
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        IdsReq req = new IdsReq();
        req.setTenantId(100L);
        req.setIds(Collections.singletonList(5L));

        service.remove(req);

        verify(mapper).updateById(entity);
        assertEquals(5L, entity.getDeleteFlag());
        assertNotNull(entity.getDeletedAt());
    }

    @Test
    void remove_cascadeDelete() {
        PcAbstractRole entity = buildRole(5L, 100L, null, null, "r", "/5", 0);
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(entity));

        PcUserRole ur = new PcUserRole();
        ur.setId(50L);
        ur.setTenantId(100L);
        ur.setAbstractRoleId(5L);
        ur.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(ur));

        PcRoleResourcePermission rrp = new PcRoleResourcePermission();
        rrp.setId(60L);
        rrp.setTenantId(100L);
        rrp.setAbstractRoleId(5L);
        rrp.setDeleteFlag(PermissionConstants.NOT_DELETED);
        when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(rrp));

        IdsReq req = new IdsReq();
        req.setTenantId(100L);
        req.setIds(Collections.singletonList(5L));

        service.remove(req);

        verify(mapper).updateById(entity);
        verify(userRoleMapper).updateById(ur);
        verify(roleResourcePermissionMapper).updateById(rrp);
        assertEquals(50L, ur.getDeleteFlag());
        assertEquals(60L, rrp.getDeleteFlag());
    }

    @Test
    void remove_tenantIsolation() {
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        IdsReq req = new IdsReq();
        req.setTenantId(999L);
        req.setIds(Collections.singletonList(5L));

        service.remove(req);

        verify(mapper, never()).updateById(any(PcAbstractRole.class));
        verifyNoInteractions(userRoleMapper);
        verifyNoInteractions(roleResourcePermissionMapper);
    }

    @Test
    void remove_nonExistentId() {
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        IdsReq req = new IdsReq();
        req.setTenantId(100L);
        req.setIds(Collections.singletonList(9999L));

        service.remove(req);

        verify(mapper, never()).updateById(any(PcAbstractRole.class));
    }

    @Test
    void save_moveRole_refreshesSubtreePaths() {
        PcAbstractRole parent = buildRole(10L, 100L, null, null, "parent", "/10", 0);
        PcAbstractRole existing = buildRole(5L, 100L, null, null, "role", "/5", 0);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(parent).thenReturn(existing);

        RoleSaveReq req = new RoleSaveReq();
        req.setId(5L);
        req.setTenantId(100L);
        req.setParentId(10L);
        req.setName("Moved");
        req.setRoleType(1);

        service.save(req);

        verify(mapper).updateById(existing);
        verify(treePathManager).refreshRoleSubtreePaths(100L, "/5", "/10/5");
    }

    @Test
    void remove_withChildren_throwsIllegalState() {
        PcAbstractRole entity = buildRole(5L, 100L, null, null, "r", "/5", 0);
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.singletonList(entity));
        doThrow(new IllegalStateException("存在未删除的子角色，禁止删除"))
            .when(treePathManager).assertRoleHasNoChildren(100L, 5L);

        IdsReq req = new IdsReq();
        req.setTenantId(100L);
        req.setIds(Collections.singletonList(5L));

        assertThrows(IllegalStateException.class, () -> service.remove(req));
        verify(mapper, never()).updateById(entity);
    }
}
