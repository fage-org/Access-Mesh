package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.dto.IdsReq;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgTreeConfigCreateReq;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgTreeConfigUpdateReq;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织树配置写方法测试。
 * <p>
 * ① ORG_VISIBILITY 失效登记（T-ACCESS-006 第二轮外部评审补测）：4 个写方法在
 * @PermissionChange 上下文中调用 {@link PermissionChangeContext#markVisibility}（flush 阶段
 * 由 PermissionChangeAspect 无条件 evictAll，框架行为另由 PermissionChangeAspectTest 覆盖）。
 * ② 默认树身份目录守卫（T-ORG-002，拍板最小面）：切默认/改默认配置根将使用户失去
 * 身份目录归属时整体拒绝（ORG_TREE_CONFIG_DEFAULT_PROTECTED，提示受影响人数）、
 * 删除默认配置行无条件拒绝、空租户与安全扩围放行。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrgTreeConfigAppServiceImplTest {

    private static final long TENANT_ID = 1000L;

    @Mock private SysOrgTreeConfigMapper orgTreeConfigMapper;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

    private OrgTreeConfigAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgTreeConfigAppServiceImpl(orgTreeConfigMapper, permissionValidator,
            orgTreeConfigDomainService, orgDomainService, treeWriteLockSupport);
        TenantContextHolder.setTenantId(TENANT_ID);
        // 模拟 @PermissionChange 切面入口绑定累积器（owner）
        assertThat(PermissionChangeContext.bindIfAbsent()).isTrue();
    }

    @AfterEach
    void tearDown() {
        PermissionChangeContext.clear();
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("写方法登记 ORG_VISIBILITY 租户级失效")
    class WriteMethodsMarkVisibility {

        @Test
        @DisplayName("createOrgTreeConfig 登记可见范围变更")
        void create_marksVisibility() {
            service.createOrgTreeConfig(new OrgTreeConfigCreateReq(1L, "默认组织树", "DEFAULT", null));

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("updateOrgTreeConfig 登记可见范围变更")
        void update_marksVisibility() {
            when(orgTreeConfigMapper.selectByIdSafe(eq(TENANT_ID), eq(1L))).thenReturn(new SysOrgTreeConfig());

            service.updateOrgTreeConfig(new OrgTreeConfigUpdateReq(1L, null, "新名称", null, null));

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("deleteOrgTreeConfigs 登记可见范围变更（无默认配置，守卫放行）")
        void delete_marksVisibility() {
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID)).thenReturn(List.of());

            service.deleteOrgTreeConfigs(new IdsReq(List.of(1L, 2L)));

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
            verify(orgTreeConfigMapper).softDeleteBatch(eq(TENANT_ID), eq(List.of(1L, 2L)), any());
        }

        @Test
        @DisplayName("setDefault 登记可见范围变更（无旧默认配置，守卫放行）")
        void setDefault_marksVisibility() {
            SysOrgTreeConfig config = new SysOrgTreeConfig();
            config.setRootOrgId(5L);
            when(orgTreeConfigMapper.selectByIdSafe(eq(TENANT_ID), eq(5L))).thenReturn(config);
            when(orgDomainService.selectValidById(TENANT_ID, 5L)).thenReturn(new SysOrg());
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID)).thenReturn(List.of());

            service.setDefault(5L);

            PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
            assertThat(acc).isNotNull();
            assertThat(acc.tenantId()).isEqualTo(TENANT_ID);
            assertThat(acc.isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("默认树身份目录守卫（T-ORG-002）")
    class DefaultTreeGuard {

        private SysOrgTreeConfig config(Long id, Long rootOrgId, boolean isDefault) {
            SysOrgTreeConfig config = new SysOrgTreeConfig();
            config.setId(id);
            config.setTenantId(TENANT_ID);
            config.setRootOrgId(rootOrgId);
            config.setTreeName("树");
            config.setTreeType("DEFAULT");
            config.setIsDefault(isDefault);
            return config;
        }

        private SysOrg org(Long id) {
            SysOrg org = new SysOrg();
            org.setId(id);
            org.setOrgType("1");
            return org;
        }

        @Test
        @DisplayName("setDefault：旧默认树上存在用户归属 → 拒绝（拍板 A 收紧：存在即拒，提示人数）")
        void setDefaultWithUsersOnOldTreeRejected() {
            SysOrgTreeConfig newConfig = config(5L, 50L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 5L)).thenReturn(newConfig);
            when(orgDomainService.selectValidById(TENANT_ID, 50L)).thenReturn(org(50L));
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID))
                .thenReturn(List.of(config(1L, 10L, true)));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 10L)).thenReturn(List.of(10L, 11L));
            when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT_ID), any(), any()))
                .thenReturn(Set.of(100L, 101L, 102L));

            assertThatThrownBy(() -> service.setDefault(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("旧默认树存在 3 名用户归属")
                .hasMessageContaining("请先迁移成员后切换");
            verify(orgTreeConfigMapper, never()).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("setDefault：扩围树（新子树 ⊇ 旧子树）且旧树有归属用户 → 同样拒绝（严格判定，评审拍板 A 分歧场景锁）")
        void setDefaultWideningTreeWithMembersRejected() {
            // 旧默认树 {10,11}，新树根 9 为 10 的祖先（子树 {9,10,11} ⊇ 旧树）——无人失去归属，
            // 但旧树存在归属用户 → 拒绝（损失判定口径会放行，本用例锁严格口径）
            SysOrgTreeConfig newConfig = config(5L, 9L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 5L)).thenReturn(newConfig);
            when(orgDomainService.selectValidById(TENANT_ID, 9L)).thenReturn(org(9L));
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID))
                .thenReturn(List.of(config(1L, 10L, true)));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 10L)).thenReturn(List.of(10L, 11L));
            when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT_ID), any(), any()))
                .thenReturn(Set.of(100L));

            assertThatThrownBy(() -> service.setDefault(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("旧默认树存在 1 名用户归属");
            verify(orgTreeConfigMapper, never()).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("setDefault：旧默认树无归属用户（空租户）→ 放行切换")
        void setDefaultOnEmptyTenantAllowed() {
            SysOrgTreeConfig newConfig = config(5L, 50L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 5L)).thenReturn(newConfig);
            when(orgDomainService.selectValidById(TENANT_ID, 50L)).thenReturn(org(50L));
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID))
                .thenReturn(List.of(config(1L, 10L, true)));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 10L)).thenReturn(List.of(10L));
            when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT_ID), any(), any()))
                .thenReturn(Set.of());

            service.setDefault(5L);

            verify(orgTreeConfigMapper).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("setDefault：目标根组织不存在 → 拒绝（ORG_NOT_FOUND）")
        void setDefaultWithMissingRootRejected() {
            SysOrgTreeConfig newConfig = config(5L, 50L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 5L)).thenReturn(newConfig);
            when(orgDomainService.selectValidById(TENANT_ID, 50L)).thenReturn(null);

            assertThatThrownBy(() -> service.setDefault(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目标根组织不存在");
            verify(orgTreeConfigMapper, never()).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("update：默认配置改根致用户失去归属 → 拒绝")
        void updateDefaultConfigRootLosingUsersRejected() {
            SysOrgTreeConfig existing = config(1L, 10L, true);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 1L)).thenReturn(existing);
            when(orgDomainService.selectValidById(TENANT_ID, 50L)).thenReturn(org(50L));
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID)).thenReturn(List.of(existing));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 10L)).thenReturn(List.of(10L, 11L));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 50L)).thenReturn(List.of(50L));
            when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT_ID), any(), any()))
                .thenReturn(Set.of(100L));

            assertThatThrownBy(() -> service.updateOrgTreeConfig(
                new OrgTreeConfigUpdateReq(1L, 50L, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("修改默认组织树根将使 1 名用户失去默认组织树身份目录归属");
            verify(orgTreeConfigMapper, never()).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("update：默认配置安全扩围（新子树包含旧子树）→ 放行")
        void updateDefaultConfigRootSafeWideningAllowed() {
            SysOrgTreeConfig existing = config(1L, 11L, true);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 1L)).thenReturn(existing);
            when(orgDomainService.selectValidById(TENANT_ID, 10L)).thenReturn(org(10L));
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID)).thenReturn(List.of(existing));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 11L)).thenReturn(List.of(11L));
            when(orgDomainService.getDescendantIdsIncludingSelf(TENANT_ID, 10L)).thenReturn(List.of(10L, 11L));
            when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT_ID), any(), any()))
                .thenReturn(Set.of());

            service.updateOrgTreeConfig(new OrgTreeConfigUpdateReq(1L, 10L, null, null, null));

            verify(orgTreeConfigMapper).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("update：非默认配置改根 → 不触发默认树守卫")
        void updateNonDefaultConfigRootSkipsGuard() {
            SysOrgTreeConfig existing = config(2L, 10L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 2L)).thenReturn(existing);

            service.updateOrgTreeConfig(new OrgTreeConfigUpdateReq(2L, 50L, null, null, null));

            verify(orgTreeConfigDomainService, never()).findUsersLosingDefaultHome(anyLong(), any(), any());
            verify(orgTreeConfigMapper).update(any(SysOrgTreeConfig.class));
        }

        @Test
        @DisplayName("delete：ids 含默认配置行 → 无条件拒绝（身份目录结构性存在）")
        void deleteDefaultConfigRejected() {
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID))
                .thenReturn(List.of(config(1L, 10L, true)));

            assertThatThrownBy(() -> service.deleteOrgTreeConfigs(new IdsReq(List.of(2L, 1L))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("默认组织树配置不允许删除")
                .hasMessageContaining("请先切换默认");
            verify(orgTreeConfigMapper, never()).softDeleteBatch(anyLong(), any(), any());
        }

        @Test
        @DisplayName("delete：无默认配置 → 放行批量软删")
        void deleteWithoutDefaultConfigAllowed() {
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID)).thenReturn(List.of());

            service.deleteOrgTreeConfigs(new IdsReq(List.of(2L, 3L)));

            verify(orgTreeConfigMapper).softDeleteBatch(eq(TENANT_ID), eq(List.of(2L, 3L)), any());
        }
    }

    @Nested
    @DisplayName("权威读取入锁（外评 R3：锁内同语句重读命中 MyBatis 会话级一级缓存）")
    class AuthoritativeReadInsideLock {

        private SysOrgTreeConfig config(Long id, Long rootOrgId, boolean isDefault) {
            SysOrgTreeConfig config = new SysOrgTreeConfig();
            config.setId(id);
            config.setTenantId(TENANT_ID);
            config.setRootOrgId(rootOrgId);
            config.setTreeName("树");
            config.setTreeType("DEFAULT");
            config.setIsDefault(isDefault);
            return config;
        }

        private SysOrg org(Long id) {
            SysOrg org = new SysOrg();
            org.setId(id);
            org.setOrgType("1");
            return org;
        }

        @Test
        @DisplayName("update：配置读取恰一次且在 SYS_ORG 锁后（旧实现锁前读+锁内重读必红）")
        void updateReadsConfigOnceAfterLock() {
            SysOrgTreeConfig existing = config(2L, 10L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 2L)).thenReturn(existing);

            service.updateOrgTreeConfig(new OrgTreeConfigUpdateReq(2L, 50L, null, null, null));

            InOrder inOrder = inOrder(treeWriteLockSupport, orgTreeConfigMapper);
            inOrder.verify(treeWriteLockSupport)
                .lockTreeWrites(TENANT_ID, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
            inOrder.verify(orgTreeConfigMapper).selectByIdSafe(TENANT_ID, 2L);
            // 恰一次：同事务同语句二次调用在会话级一级缓存下返回锁前实例（不访问库），
            // 「锁内重读」形同虚设——结构性回归锁：权威读取=锁内首次读取，不得恢复双读
            verify(orgTreeConfigMapper, times(1)).selectByIdSafe(TENANT_ID, 2L);
        }

        @Test
        @DisplayName("setDefault：配置读取恰一次且在 SYS_ORG 锁后（旧实现锁前读+锁内重读必红）")
        void setDefaultReadsConfigOnceAfterLock() {
            SysOrgTreeConfig newConfig = config(5L, 50L, false);
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 5L)).thenReturn(newConfig);
            when(orgDomainService.selectValidById(TENANT_ID, 50L)).thenReturn(org(50L));
            when(orgTreeConfigDomainService.findDefaultConfigs(TENANT_ID)).thenReturn(List.of());

            service.setDefault(5L);

            InOrder inOrder = inOrder(treeWriteLockSupport, orgTreeConfigMapper);
            inOrder.verify(treeWriteLockSupport)
                .lockTreeWrites(TENANT_ID, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
            inOrder.verify(orgTreeConfigMapper).selectByIdSafe(TENANT_ID, 5L);
            verify(orgTreeConfigMapper, times(1)).selectByIdSafe(TENANT_ID, 5L);
        }

        @Test
        @DisplayName("update：无 orgId 纯字段更新同样持锁（防 update 全列回写覆盖并发 setDefault 的 isDefault，旧实现必红）")
        void updateWithoutOrgIdAlsoLocks() {
            when(orgTreeConfigMapper.selectByIdSafe(TENANT_ID, 1L)).thenReturn(config(1L, 10L, false));

            service.updateOrgTreeConfig(new OrgTreeConfigUpdateReq(1L, null, "新名称", null, null));

            verify(treeWriteLockSupport).lockTreeWrites(TENANT_ID, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        }
    }
}
