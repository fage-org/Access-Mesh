package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.*;
import org.dromara.permission.mapper.*;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.support.PermissionTreePathManager;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionSyncServiceImplTest {

    private static final long NOT_DELETED = 0L;
    private static final int BATCH_LIMIT = 500;

    @Mock
    private PcAbstractUserMapper abstractUserMapper;
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;
    @Mock
    private PcResourceEntityMapper resourceEntityMapper;
    @Mock
    private PcOperationPermissionMapper operationPermissionMapper;
    @Mock
    private PcPermissionConditionMapper permissionConditionMapper;
    @Mock
    private PcUserRoleMapper userRoleMapper;
    @Mock
    private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private PermissionChangeLogService permissionChangeLogService;
    @Mock
    private PermissionTreePathManager treePathManager;
    @Mock
    private TypeDefinitionReader typeDefinitionReader;

    @InjectMocks
    private PermissionSyncServiceImpl syncService;

    private final AtomicLong idSeq = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        idSeq.set(1000);
        lenient().doAnswer(inv -> {
            String parentPath = inv.getArgument(0);
            Long id = inv.getArgument(1);
            return parentPath == null ? "/" + id : parentPath + "/" + id;
        }).when(treePathManager).buildPath(any(), any());
    }

    // ========================= syncUsers =========================

    @Nested
    @DisplayName("syncUsers")
    class SyncUsersTests {

        @Test
        @DisplayName("insertNew - 新 externalId 触发 insert")
        void syncUsers_insertNew() {
            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            SyncUsersReq.SyncUserItem item = new SyncUsersReq.SyncUserItem();
            item.setExternalId("ext-001");
            item.setName("Alice");
            item.setExtra("{\"dept\":\"IT\"}");
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                PcAbstractUser u = inv.getArgument(0);
                u.setId(idSeq.getAndIncrement());
                return 1;
            }).when(abstractUserMapper).insert(any(PcAbstractUser.class));

            syncService.syncUsers(req);

            ArgumentCaptor<PcAbstractUser> captor = ArgumentCaptor.forClass(PcAbstractUser.class);
            verify(abstractUserMapper).insert(captor.capture());
            PcAbstractUser saved = captor.getValue();
            assertThat(saved.getTenantId()).isEqualTo(1L);
            assertThat(saved.getUserType()).isEqualTo(1);
            assertThat(saved.getExternalId()).isEqualTo("ext-001");
            assertThat(saved.getName()).isEqualTo("Alice");
            assertThat(saved.getExtra()).isEqualTo("{\"dept\":\"IT\"}");
            assertThat(saved.getDeleteFlag()).isEqualTo(NOT_DELETED);

            verify(abstractUserMapper, never()).updateById(any(PcAbstractUser.class));
            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), isNull(), eq("sync_user"), eq(0L), eq("INSERT"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("updateExisting - 已存在用户触发 update")
        void syncUsers_updateExisting() {
            PcAbstractUser existing = new PcAbstractUser();
            existing.setId(100L);
            existing.setTenantId(1L);
            existing.setUserType(1);
            existing.setExternalId("ext-001");
            existing.setName("OldName");
            existing.setExtra("{}");
            existing.setDeleteFlag(NOT_DELETED);

            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            SyncUsersReq.SyncUserItem item = new SyncUsersReq.SyncUserItem();
            item.setExternalId("ext-001");
            item.setName("NewName");
            item.setExtra("{\"v\":2}");
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing));
            when(abstractUserMapper.updateById(any(PcAbstractUser.class))).thenReturn(1);

            syncService.syncUsers(req);

            ArgumentCaptor<PcAbstractUser> captor = ArgumentCaptor.forClass(PcAbstractUser.class);
            verify(abstractUserMapper).updateById(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("NewName");
            assertThat(captor.getValue().getExtra()).isEqualTo("{\"v\":2}");
            verify(abstractUserMapper, never()).insert(any(PcAbstractUser.class));
            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), isNull(), eq("sync_user"), eq(0L), eq("UPDATE"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("mixedInsertUpdate - 混合新增和更新")
        void syncUsers_mixedInsertUpdate() {
            PcAbstractUser existing = new PcAbstractUser();
            existing.setId(100L);
            existing.setTenantId(1L);
            existing.setUserType(1);
            existing.setExternalId("ext-001");
            existing.setName("OldName");
            existing.setDeleteFlag(NOT_DELETED);

            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);

            SyncUsersReq.SyncUserItem updateItem = new SyncUsersReq.SyncUserItem();
            updateItem.setExternalId("ext-001");
            updateItem.setName("Updated");

            SyncUsersReq.SyncUserItem newItem = new SyncUsersReq.SyncUserItem();
            newItem.setExternalId("ext-002");
            newItem.setName("Brand New");
            newItem.setExtra("{\"new\":true}");

            req.setItems(List.of(updateItem, newItem));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing));
            doAnswer(inv -> {
                PcAbstractUser u = inv.getArgument(0);
                u.setId(idSeq.getAndIncrement());
                return 1;
            }).when(abstractUserMapper).insert(any(PcAbstractUser.class));
            when(abstractUserMapper.updateById(any(PcAbstractUser.class))).thenReturn(1);

            syncService.syncUsers(req);

            verify(abstractUserMapper, times(1)).insert(any(PcAbstractUser.class));
            verify(abstractUserMapper, times(1)).updateById(any(PcAbstractUser.class));
            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), isNull(), eq("sync_user"), eq(0L), eq("INSERT_UPDATE"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("emptyItems - 空列表不触发任何 mapper 调用")
        void syncUsers_emptyItems() {
            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            req.setItems(Collections.emptyList());

            syncService.syncUsers(req);

            verifyNoInteractions(abstractUserMapper);
            verifyNoInteractions(permissionChangeLogService);
        }

        @Test
        @DisplayName("exceedBatchLimit - 超过500条抛 ServiceException")
        void syncUsers_exceedBatchLimit() {
            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            List<SyncUsersReq.SyncUserItem> items = new ArrayList<>();
            for (int i = 0; i < BATCH_LIMIT + 1; i++) {
                SyncUsersReq.SyncUserItem it = new SyncUsersReq.SyncUserItem();
                it.setExternalId("ext-" + i);
                it.setName("user-" + i);
                items.add(it);
            }
            req.setItems(items);

            assertThatThrownBy(() -> syncService.syncUsers(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("500");
        }

        @Test
        @DisplayName("extraNullDefaultsToEmptyJson - extra 为 null 时默认 {}")
        void syncUsers_extraNullDefaultsToEmptyJson() {
            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            SyncUsersReq.SyncUserItem item = new SyncUsersReq.SyncUserItem();
            item.setExternalId("ext-null");
            item.setName("NoExtra");
            item.setExtra(null);
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                PcAbstractUser u = inv.getArgument(0);
                u.setId(idSeq.getAndIncrement());
                return 1;
            }).when(abstractUserMapper).insert(any(PcAbstractUser.class));

            syncService.syncUsers(req);

            ArgumentCaptor<PcAbstractUser> captor = ArgumentCaptor.forClass(PcAbstractUser.class);
            verify(abstractUserMapper).insert(captor.capture());
            assertThat(captor.getValue().getExtra()).isEqualTo("{}");
        }

        @Test
        @DisplayName("duplicateExternalIdInDb - toMap merge 保留第一条")
        void syncUsers_duplicateExternalIdInDb() {
            PcAbstractUser first = new PcAbstractUser();
            first.setId(100L);
            first.setExternalId("dup");
            first.setName("First");
            first.setDeleteFlag(NOT_DELETED);

            PcAbstractUser second = new PcAbstractUser();
            second.setId(200L);
            second.setExternalId("dup");
            second.setName("Second");
            second.setDeleteFlag(NOT_DELETED);

            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            SyncUsersReq.SyncUserItem item = new SyncUsersReq.SyncUserItem();
            item.setExternalId("dup");
            item.setName("UpdatedName");
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(first, second));
            when(abstractUserMapper.updateById(any(PcAbstractUser.class))).thenReturn(1);

            syncService.syncUsers(req);

            ArgumentCaptor<PcAbstractUser> captor = ArgumentCaptor.forClass(PcAbstractUser.class);
            verify(abstractUserMapper).updateById(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(100L);
            assertThat(captor.getValue().getName()).isEqualTo("UpdatedName");
        }
    }

    // ========================= syncRoles =========================

    @Nested
    @DisplayName("syncRoles")
    class SyncRolesTests {

        @Test
        @DisplayName("insertRootRole - 无父角色时 path = /id")
        void syncRoles_insertRootRole() {
            SyncRolesReq req = new SyncRolesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);
            SyncRolesReq.SyncRoleItem item = new SyncRolesReq.SyncRoleItem();
            item.setExternalId("role-root");
            item.setRoleType(1);
            item.setName("RootRole");
            item.setSortOrder(0);
            req.setItems(List.of(item));

            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            final long assignedId = 500L;
            doAnswer(inv -> {
                PcAbstractRole r = inv.getArgument(0);
                r.setId(assignedId);
                return 1;
            }).when(abstractRoleMapper).insert(any(PcAbstractRole.class));
            when(abstractRoleMapper.updateById(any(PcAbstractRole.class))).thenReturn(1);

            syncService.syncRoles(req);

            ArgumentCaptor<PcAbstractRole> captor = ArgumentCaptor.forClass(PcAbstractRole.class);
            verify(abstractRoleMapper).updateById(captor.capture());
            assertThat(captor.getValue().getPath()).isEqualTo("/" + assignedId);
        }

        @Test
        @DisplayName("insertChildRole - 批内有父角色时正确设置 parentId 和 path")
        void syncRoles_insertChildRole() {
            SyncRolesReq req = new SyncRolesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);

            SyncRolesReq.SyncRoleItem parent = new SyncRolesReq.SyncRoleItem();
            parent.setExternalId("parent-ext");
            parent.setRoleType(1);
            parent.setName("Parent");

            SyncRolesReq.SyncRoleItem child = new SyncRolesReq.SyncRoleItem();
            child.setExternalId("child-ext");
            child.setRoleType(1);
            child.setName("Child");
            child.setParentExternalId("parent-ext");

            req.setItems(List.of(child, parent));

            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            AtomicLong seq = new AtomicLong(1);
            doAnswer(inv -> {
                PcAbstractRole r = inv.getArgument(0);
                r.setId(seq.getAndIncrement());
                return 1;
            }).when(abstractRoleMapper).insert(any(PcAbstractRole.class));
            when(abstractRoleMapper.updateById(any(PcAbstractRole.class))).thenReturn(1);

            syncService.syncRoles(req);

            ArgumentCaptor<PcAbstractRole> captor = ArgumentCaptor.forClass(PcAbstractRole.class);
            verify(abstractRoleMapper, atLeast(2)).updateById(captor.capture());

            List<PcAbstractRole> updates = captor.getAllValues();
            PcAbstractRole childEntity = updates.stream()
                .filter(r -> "child-ext".equals(r.getExternalId()) && r.getPath() != null)
                .findFirst().orElse(null);

            if (childEntity != null) {
                assertThat(childEntity.getParentId()).isNotNull();
                assertThat(childEntity.getPath()).contains("/");
            }
        }

        @Test
        @DisplayName("circularDependency - A→B→A 抛 ServiceException 含 循环依赖")
        void syncRoles_circularDependency() {
            SyncRolesReq req = new SyncRolesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);

            SyncRolesReq.SyncRoleItem a = new SyncRolesReq.SyncRoleItem();
            a.setExternalId("A");
            a.setRoleType(1);
            a.setName("A");
            a.setParentExternalId("B");

            SyncRolesReq.SyncRoleItem b = new SyncRolesReq.SyncRoleItem();
            b.setExternalId("B");
            b.setRoleType(1);
            b.setName("B");
            b.setParentExternalId("A");

            req.setItems(List.of(a, b));

            assertThatThrownBy(() -> syncService.syncRoles(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("循环依赖");
        }

        @Test
        @DisplayName("parentNotInBatchButInDb - 父在 DB 中存在时正确解析")
        void syncRoles_parentNotInBatchButInDb() {
            PcAbstractRole dbParent = new PcAbstractRole();
            dbParent.setId(999L);
            dbParent.setExternalId("db-parent");
            dbParent.setPath("/999");
            dbParent.setDeleteFlag(NOT_DELETED);

            SyncRolesReq req = new SyncRolesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);

            SyncRolesReq.SyncRoleItem child = new SyncRolesReq.SyncRoleItem();
            child.setExternalId("child-ext");
            child.setRoleType(1);
            child.setName("Child");
            child.setParentExternalId("db-parent");

            req.setItems(List.of(child));

            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(dbParent));

            final long childId = 500L;
            doAnswer(inv -> {
                PcAbstractRole r = inv.getArgument(0);
                r.setId(childId);
                return 1;
            }).when(abstractRoleMapper).insert(any(PcAbstractRole.class));
            when(abstractRoleMapper.updateById(any(PcAbstractRole.class))).thenReturn(1);

            syncService.syncRoles(req);

            ArgumentCaptor<PcAbstractRole> captor = ArgumentCaptor.forClass(PcAbstractRole.class);
            verify(abstractRoleMapper, atLeastOnce()).updateById(captor.capture());
            PcAbstractRole updated = captor.getAllValues().stream()
                .filter(r -> r.getPath() != null && r.getPath().startsWith("/999/"))
                .findFirst().orElse(null);
            if (updated != null) {
                assertThat(updated.getPath()).isEqualTo("/999/" + childId);
                assertThat(updated.getParentId()).isEqualTo(999L);
            }
        }

        @Test
        @DisplayName("exceedBatchLimit - 超过500条抛 ServiceException")
        void syncRoles_exceedBatchLimit() {
            SyncRolesReq req = new SyncRolesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);
            List<SyncRolesReq.SyncRoleItem> items = new ArrayList<>();
            for (int i = 0; i < BATCH_LIMIT + 1; i++) {
                SyncRolesReq.SyncRoleItem it = new SyncRolesReq.SyncRoleItem();
                it.setExternalId("r-" + i);
                it.setRoleType(1);
                it.setName("Role" + i);
                items.add(it);
            }
            req.setItems(items);

            assertThatThrownBy(() -> syncService.syncRoles(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("500");
        }

        @Test
        @DisplayName("bizDomainIdNull - bizDomainId 为 null 构建 isNull 查询")
        void syncRoles_bizDomainIdNull() {
            SyncRolesReq req = new SyncRolesReq();
            req.setTenantId(1L);
            req.setBizDomainId(null);
            SyncRolesReq.SyncRoleItem item = new SyncRolesReq.SyncRoleItem();
            item.setExternalId("global-role");
            item.setRoleType(1);
            item.setName("GlobalRole");
            req.setItems(List.of(item));

            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                PcAbstractRole r = inv.getArgument(0);
                r.setId(idSeq.getAndIncrement());
                return 1;
            }).when(abstractRoleMapper).insert(any(PcAbstractRole.class));
            when(abstractRoleMapper.updateById(any(PcAbstractRole.class))).thenReturn(1);

            syncService.syncRoles(req);

            verify(abstractRoleMapper, atLeastOnce()).selectList(any(LambdaQueryWrapper.class));
            verify(abstractRoleMapper).insert(any(PcAbstractRole.class));
        }
    }

    // ========================= syncResources =========================

    @Nested
    @DisplayName("syncResources")
    class SyncResourcesTests {

        @Test
        @DisplayName("insertRoot - 根资源 path = /id")
        void syncResources_insertRoot() {
            SyncResourcesReq req = new SyncResourcesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);
            req.setResourceType(1);

            SyncResourcesReq.SyncResourceItem item = new SyncResourcesReq.SyncResourceItem();
            item.setCode("res-root");
            item.setName("RootRes");
            req.setItems(List.of(item));

            when(resourceEntityMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            final long assignedId = 600L;
            doAnswer(inv -> {
                PcResourceEntity e = inv.getArgument(0);
                e.setId(assignedId);
                return 1;
            }).when(resourceEntityMapper).insert(any(PcResourceEntity.class));
            when(resourceEntityMapper.updateById(any(PcResourceEntity.class))).thenReturn(1);

            syncService.syncResources(req);

            ArgumentCaptor<PcResourceEntity> captor = ArgumentCaptor.forClass(PcResourceEntity.class);
            verify(resourceEntityMapper, atLeastOnce()).updateById(captor.capture());
            PcResourceEntity pathUpdate = captor.getAllValues().stream()
                .filter(r -> r.getPath() != null).findFirst().orElseThrow();
            assertThat(pathUpdate.getPath()).isEqualTo("/" + assignedId);
        }

        @Test
        @DisplayName("circularDependency - 资源循环依赖抛 ServiceException 含 循环依赖")
        void syncResources_circularDependency() {
            SyncResourcesReq req = new SyncResourcesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);
            req.setResourceType(1);

            SyncResourcesReq.SyncResourceItem a = new SyncResourcesReq.SyncResourceItem();
            a.setCode("A");
            a.setName("A");
            a.setParentCode("B");

            SyncResourcesReq.SyncResourceItem b = new SyncResourcesReq.SyncResourceItem();
            b.setCode("B");
            b.setName("B");
            b.setParentCode("A");

            req.setItems(List.of(a, b));

            assertThatThrownBy(() -> syncService.syncResources(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("循环依赖");
        }

        @Test
        @DisplayName("exceedBatchLimit - 超过500条抛 ServiceException")
        void syncResources_exceedBatchLimit() {
            SyncResourcesReq req = new SyncResourcesReq();
            req.setTenantId(1L);
            req.setBizDomainId(10L);
            req.setResourceType(1);
            List<SyncResourcesReq.SyncResourceItem> items = new ArrayList<>();
            for (int i = 0; i < BATCH_LIMIT + 1; i++) {
                SyncResourcesReq.SyncResourceItem it = new SyncResourcesReq.SyncResourceItem();
                it.setCode("c-" + i);
                it.setName("Res" + i);
                items.add(it);
            }
            req.setItems(items);

            assertThatThrownBy(() -> syncService.syncResources(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("500");
        }
    }

    // ========================= syncUserRoles =========================

    @Nested
    @DisplayName("syncUserRoles")
    class SyncUserRolesTests {

        private PcAbstractUser makeUser(Long id, String externalId, Integer userType) {
            PcAbstractUser u = new PcAbstractUser();
            u.setId(id);
            u.setTenantId(1L);
            u.setUserType(userType);
            u.setExternalId(externalId);
            u.setDeleteFlag(NOT_DELETED);
            return u;
        }

        private PcAbstractRole makeRole(Long id, String externalId) {
            PcAbstractRole r = new PcAbstractRole();
            r.setId(id);
            r.setTenantId(1L);
            r.setExternalId(externalId);
            r.setDeleteFlag(NOT_DELETED);
            return r;
        }

        @Test
        @DisplayName("insertNew - 新 user-role 关联触发 insert")
        void syncUserRoles_insertNew() {
            PcAbstractUser user = makeUser(10L, "u1", 1);
            PcAbstractRole role = makeRole(20L, "r1");

            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);
            SyncUserRolesReq.SyncUserRoleItem item = new SyncUserRolesReq.SyncUserRoleItem();
            item.setUserExternalId("u1");
            item.setUserType(1);
            item.setRoleExternalId("r1");
            item.setValidFrom(LocalDateTime.of(2025, 1, 1, 0, 0));
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user));
            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(role));
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                PcUserRole ur = inv.getArgument(0);
                ur.setId(idSeq.getAndIncrement());
                return 1;
            }).when(userRoleMapper).insert(any(PcUserRole.class));

            syncService.syncUserRoles(req);

            ArgumentCaptor<PcUserRole> captor = ArgumentCaptor.forClass(PcUserRole.class);
            verify(userRoleMapper).insert(captor.capture());
            PcUserRole saved = captor.getValue();
            assertThat(saved.getAbstractUserId()).isEqualTo(10L);
            assertThat(saved.getAbstractRoleId()).isEqualTo(20L);
            assertThat(saved.getValidFrom()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
            assertThat(saved.getDeleteFlag()).isEqualTo(NOT_DELETED);

            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), isNull(), eq("sync_user_role"), eq(0L), eq("INSERT"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("updateExisting - 已有关联触发 update validFrom/validTo")
        void syncUserRoles_updateExisting() {
            PcAbstractUser user = makeUser(10L, "u1", 1);
            PcAbstractRole role = makeRole(20L, "r1");

            PcUserRole existingUr = new PcUserRole();
            existingUr.setId(300L);
            existingUr.setTenantId(1L);
            existingUr.setAbstractUserId(10L);
            existingUr.setAbstractRoleId(20L);
            existingUr.setDeleteFlag(NOT_DELETED);

            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);
            SyncUserRolesReq.SyncUserRoleItem item = new SyncUserRolesReq.SyncUserRoleItem();
            item.setUserExternalId("u1");
            item.setUserType(1);
            item.setRoleExternalId("r1");
            item.setValidFrom(LocalDateTime.of(2025, 6, 1, 0, 0));
            item.setValidTo(LocalDateTime.of(2025, 12, 31, 23, 59));
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user));
            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(role));
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existingUr));
            when(userRoleMapper.updateById(any(PcUserRole.class))).thenReturn(1);

            syncService.syncUserRoles(req);

            ArgumentCaptor<PcUserRole> captor = ArgumentCaptor.forClass(PcUserRole.class);
            verify(userRoleMapper).updateById(captor.capture());
            PcUserRole updated = captor.getValue();
            assertThat(updated.getValidFrom()).isEqualTo(LocalDateTime.of(2025, 6, 1, 0, 0));
            assertThat(updated.getValidTo()).isEqualTo(LocalDateTime.of(2025, 12, 31, 23, 59));
            verify(userRoleMapper, never()).insert(any(PcUserRole.class));
        }

        @Test
        @DisplayName("userNotFound - 用户不存在时跳过（log warning）")
        void syncUserRoles_userNotFound() {
            PcAbstractRole role = makeRole(20L, "r1");

            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);
            SyncUserRolesReq.SyncUserRoleItem item = new SyncUserRolesReq.SyncUserRoleItem();
            item.setUserExternalId("missing-user");
            item.setUserType(1);
            item.setRoleExternalId("r1");
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(role));

            syncService.syncUserRoles(req);

            verify(userRoleMapper, never()).insert(any(PcUserRole.class));
            verify(userRoleMapper, never()).updateById(any(PcUserRole.class));
            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), isNull(), eq("sync_user_role"), eq(0L), eq("NOOP"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("roleNotFound - 角色不存在时跳过（log warning）")
        void syncUserRoles_roleNotFound() {
            PcAbstractUser user = makeUser(10L, "u1", 1);

            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);
            SyncUserRolesReq.SyncUserRoleItem item = new SyncUserRolesReq.SyncUserRoleItem();
            item.setUserExternalId("u1");
            item.setUserType(1);
            item.setRoleExternalId("missing-role");
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user));
            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            syncService.syncUserRoles(req);

            verify(userRoleMapper, never()).insert(any(PcUserRole.class));
            verify(userRoleMapper, never()).updateById(any(PcUserRole.class));
        }

        @Test
        @DisplayName("duplicateSkipped - 同 user+role 重复条目只处理一次")
        void syncUserRoles_duplicateSkipped() {
            PcAbstractUser user = makeUser(10L, "u1", 1);
            PcAbstractRole role = makeRole(20L, "r1");

            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);

            SyncUserRolesReq.SyncUserRoleItem item1 = new SyncUserRolesReq.SyncUserRoleItem();
            item1.setUserExternalId("u1");
            item1.setUserType(1);
            item1.setRoleExternalId("r1");

            SyncUserRolesReq.SyncUserRoleItem item2 = new SyncUserRolesReq.SyncUserRoleItem();
            item2.setUserExternalId("u1");
            item2.setUserType(1);
            item2.setRoleExternalId("r1");

            req.setItems(List.of(item1, item2));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user));
            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(role));
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                PcUserRole ur = inv.getArgument(0);
                ur.setId(idSeq.getAndIncrement());
                return 1;
            }).when(userRoleMapper).insert(any(PcUserRole.class));

            syncService.syncUserRoles(req);

            verify(userRoleMapper, times(1)).insert(any(PcUserRole.class));
        }

        @Test
        @DisplayName("deleteNotInList - 开启删除标记后逻辑删除不在列表中的关联")
        void syncUserRoles_deleteNotInList() {
            PcAbstractUser user = makeUser(10L, "u1", 1);
            PcAbstractRole role1 = makeRole(20L, "r1");
            PcAbstractRole role2 = makeRole(30L, "r2");

            PcUserRole existingUr1 = new PcUserRole();
            existingUr1.setId(301L);
            existingUr1.setTenantId(1L);
            existingUr1.setAbstractUserId(10L);
            existingUr1.setAbstractRoleId(20L);
            existingUr1.setDeleteFlag(NOT_DELETED);

            PcUserRole absentUr = new PcUserRole();
            absentUr.setId(302L);
            absentUr.setTenantId(1L);
            absentUr.setAbstractUserId(10L);
            absentUr.setAbstractRoleId(30L);
            absentUr.setDeleteFlag(NOT_DELETED);

            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);
            req.setDeleteNotInList(true);

            SyncUserRolesReq.SyncUserRoleItem item = new SyncUserRolesReq.SyncUserRoleItem();
            item.setUserExternalId("u1");
            item.setUserType(1);
            item.setRoleExternalId("r1");
            req.setItems(List.of(item));

            when(abstractUserMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(user));
            when(abstractRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(role1, role2));
            when(userRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existingUr1))
                .thenReturn(List.of(existingUr1, absentUr));
            when(userRoleMapper.updateById(any(PcUserRole.class))).thenReturn(1);

            syncService.syncUserRoles(req);

            ArgumentCaptor<PcUserRole> captor = ArgumentCaptor.forClass(PcUserRole.class);
            verify(userRoleMapper, atLeast(2)).updateById(captor.capture());

            PcUserRole logicDeleted = captor.getAllValues().stream()
                .filter(ur -> ur.getId().equals(302L) && ur.getDeleteFlag().equals(302L))
                .findFirst().orElse(null);
            assertThat(logicDeleted).isNotNull();
            assertThat(logicDeleted.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("exceedBatchLimit - 超过500条抛 ServiceException")
        void syncUserRoles_exceedBatchLimit() {
            SyncUserRolesReq req = new SyncUserRolesReq();
            req.setTenantId(1L);
            List<SyncUserRolesReq.SyncUserRoleItem> items = new ArrayList<>();
            for (int i = 0; i < BATCH_LIMIT + 1; i++) {
                SyncUserRolesReq.SyncUserRoleItem it = new SyncUserRolesReq.SyncUserRoleItem();
                it.setUserExternalId("u" + i);
                it.setUserType(1);
                it.setRoleExternalId("r" + i);
                items.add(it);
            }
            req.setItems(items);

            assertThatThrownBy(() -> syncService.syncUserRoles(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("500");
        }
    }

    // ========================= syncRolePermissions =========================

    @Nested
    @DisplayName("syncRolePermissions")
    class SyncRolePermissionsTests {

        private PcAbstractRole makeRole(Long id, String externalId) {
            PcAbstractRole r = new PcAbstractRole();
            r.setId(id);
            r.setTenantId(1L);
            r.setExternalId(externalId);
            r.setDeleteFlag(NOT_DELETED);
            return r;
        }

        private PcResourceEntity makeResource(Long id, String code) {
            PcResourceEntity e = new PcResourceEntity();
            e.setId(id);
            e.setTenantId(1L);
            e.setCode(code);
            e.setDeleteFlag(NOT_DELETED);
            return e;
        }

        private PcOperationPermission makeOp(Long id, String code) {
            PcOperationPermission op = new PcOperationPermission();
            op.setId(id);
            op.setTenantId(1L);
            op.setCode(code);
            op.setDeleteFlag(NOT_DELETED);
            return op;
        }

        @Test
        @DisplayName("insertNew - 新权限触发 insert")
        void syncRolePermissions_insertNew() {
            PcAbstractRole role = makeRole(50L, "role-1");
            PcResourceEntity res = makeResource(60L, "menu:dashboard");
            PcOperationPermission op = makeOp(70L, "VIEW");

            SyncRolePermissionsReq req = new SyncRolePermissionsReq();
            req.setTenantId(1L);
            req.setRoleExternalId("role-1");
            req.setBizDomainId(10L);
            SyncRolePermissionsReq.SyncRolePermissionItem item = new SyncRolePermissionsReq.SyncRolePermissionItem();
            item.setResourceCode("menu:dashboard");
            item.setOperationCode("VIEW");
            item.setCanManage(false);
            req.setItems(List.of(item));

            when(abstractRoleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(role);
            when(resourceEntityMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(res));
            when(operationPermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(op));
            when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            doAnswer(inv -> {
                PcRoleResourcePermission rrp = inv.getArgument(0);
                rrp.setId(idSeq.getAndIncrement());
                return 1;
            }).when(roleResourcePermissionMapper).insert(any(PcRoleResourcePermission.class));

            syncService.syncRolePermissions(req);

            ArgumentCaptor<PcRoleResourcePermission> captor = ArgumentCaptor.forClass(PcRoleResourcePermission.class);
            verify(roleResourcePermissionMapper).insert(captor.capture());
            PcRoleResourcePermission saved = captor.getValue();
            assertThat(saved.getAbstractRoleId()).isEqualTo(50L);
            assertThat(saved.getResourceEntityId()).isEqualTo(60L);
            assertThat(saved.getOperationPermissionId()).isEqualTo(70L);
            assertThat(saved.getCanManage()).isFalse();
            assertThat(saved.getDeleteFlag()).isEqualTo(NOT_DELETED);

            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), eq(10L), eq("sync_role_permission"), eq(0L), eq("INSERT"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("roleNotFound - 角色不存在时抛 ServiceException")
        void syncRolePermissions_roleNotFound() {
            SyncRolePermissionsReq req = new SyncRolePermissionsReq();
            req.setTenantId(1L);
            req.setRoleExternalId("ghost-role");
            req.setBizDomainId(10L);
            SyncRolePermissionsReq.SyncRolePermissionItem item = new SyncRolePermissionsReq.SyncRolePermissionItem();
            item.setResourceCode("x");
            item.setOperationCode("y");
            req.setItems(List.of(item));

            when(abstractRoleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);

            assertThatThrownBy(() -> syncService.syncRolePermissions(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ghost-role");
        }

        @Test
        @DisplayName("resourceNotFound - 资源不存在时跳过并打 log")
        void syncRolePermissions_resourceNotFound() {
            PcAbstractRole role = makeRole(50L, "role-1");
            PcOperationPermission op = makeOp(70L, "VIEW");

            SyncRolePermissionsReq req = new SyncRolePermissionsReq();
            req.setTenantId(1L);
            req.setRoleExternalId("role-1");
            req.setBizDomainId(10L);
            SyncRolePermissionsReq.SyncRolePermissionItem item = new SyncRolePermissionsReq.SyncRolePermissionItem();
            item.setResourceCode("nonexistent");
            item.setOperationCode("VIEW");
            req.setItems(List.of(item));

            when(abstractRoleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(role);
            when(resourceEntityMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
            when(operationPermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(op));
            when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            syncService.syncRolePermissions(req);

            verify(roleResourcePermissionMapper, never()).insert(any(PcRoleResourcePermission.class));
            verify(permissionChangeLogService).writeChangeLog(
                eq(1L), eq(10L), eq("sync_role_permission"), eq(0L), eq("NOOP"), isNull(), eq(req), isNull(), eq("API"));
        }

        @Test
        @DisplayName("deleteNotInList - 移除不在列表中的权限")
        void syncRolePermissions_deleteNotInList() {
            PcAbstractRole role = makeRole(50L, "role-1");
            PcResourceEntity res = makeResource(60L, "menu:dashboard");
            PcOperationPermission op = makeOp(70L, "VIEW");

            PcRoleResourcePermission keepPerm = new PcRoleResourcePermission();
            keepPerm.setId(800L);
            keepPerm.setTenantId(1L);
            keepPerm.setAbstractRoleId(50L);
            keepPerm.setResourceEntityId(60L);
            keepPerm.setOperationPermissionId(70L);
            keepPerm.setDeleteFlag(NOT_DELETED);

            PcRoleResourcePermission removePerm = new PcRoleResourcePermission();
            removePerm.setId(801L);
            removePerm.setTenantId(1L);
            removePerm.setAbstractRoleId(50L);
            removePerm.setResourceEntityId(61L);
            removePerm.setOperationPermissionId(71L);
            removePerm.setDeleteFlag(NOT_DELETED);

            SyncRolePermissionsReq req = new SyncRolePermissionsReq();
            req.setTenantId(1L);
            req.setRoleExternalId("role-1");
            req.setBizDomainId(10L);
            req.setDeleteNotInList(true);

            SyncRolePermissionsReq.SyncRolePermissionItem item = new SyncRolePermissionsReq.SyncRolePermissionItem();
            item.setResourceCode("menu:dashboard");
            item.setOperationCode("VIEW");
            item.setCanManage(false);
            req.setItems(List.of(item));

            when(abstractRoleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(role);
            when(resourceEntityMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(res));
            when(operationPermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(op));
            when(roleResourcePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(keepPerm, removePerm));
            when(roleResourcePermissionMapper.updateById(any(PcRoleResourcePermission.class))).thenReturn(1);

            syncService.syncRolePermissions(req);

            ArgumentCaptor<PcRoleResourcePermission> captor = ArgumentCaptor.forClass(PcRoleResourcePermission.class);
            verify(roleResourcePermissionMapper, atLeast(2)).updateById(captor.capture());

            PcRoleResourcePermission logicDeleted = captor.getAllValues().stream()
                .filter(rrp -> rrp.getId().equals(801L) && rrp.getDeleteFlag().equals(801L))
                .findFirst().orElse(null);
            assertThat(logicDeleted).isNotNull();
            assertThat(logicDeleted.getDeletedAt()).isNotNull();
        }
    }

    // ========================= buildOperation (via sync methods) =========================

    @Nested
    @DisplayName("buildOperation")
    class BuildOperationTests {

        @Test
        @DisplayName("allSkipped_returnsNoop - 全部跳过时 changeLog operation 为 NOOP")
        void syncUsers_allSkipped_returnsNoop() {
            PcAbstractUser existing = new PcAbstractUser();
            existing.setId(100L);
            existing.setExternalId("ext-x");
            existing.setName("Same");
            existing.setDeleteFlag(NOT_DELETED);

            SyncUsersReq req = new SyncUsersReq();
            req.setTenantId(1L);
            req.setUserType(1);
            req.setItems(Collections.emptyList());

            syncService.syncUsers(req);

            verifyNoInteractions(permissionChangeLogService);
        }
    }
}
