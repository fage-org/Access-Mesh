package cn.ac.fage.accessmesh.access.org.service;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.impl.UserOrgAppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * setPrimaryOrg 树锁单测（claude 外评 P2 处置回归锁，与 assign/remove 同族补齐）：
 * 设主的守卫读（默认树范围解析/目标归属）与 is_primary 双写必须在 SYS_ORG 树锁内——
 * 否则与并发 deleteOrg（持同锁级联软删归属行）交错时清旧主滤掉已删行、置新主命中
 * 0 行，接口 200 但默认树主归属静默丢失。确定性证明=单测 InOrder 锁序 verify
 * （TreeCycleHardeningPgIT:308 先例口径）；旧实现（无锁）下 InOrder 必红。
 */
@ExtendWith(MockitoExtension.class)
class UserOrgAppServiceTest {

    private static final Long TENANT = 1L;
    private static final long USER = 701L;
    private static final long ORG_A = 702L;

    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private UserOrgWriteAppService userOrgWriteAppService;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

    private UserOrgAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserOrgAppServiceImpl(userOrgDomainService, orgTreeConfigDomainService,
            orgDomainService, permissionValidator, userOrgWriteAppService, treeWriteLockSupport);
    }

    @Test
    @DisplayName("设主守卫读与 is_primary 双写全部在 SYS_ORG 锁内（旧实现无锁必红）")
    void setPrimaryOrgAcquiresSysOrgLockBeforeGuardReadsAndWrite() {
        SysOrg org = new SysOrg();
        org.setId(ORG_A);
        org.setOrgType("1");
        when(orgDomainService.selectValidById(TENANT, ORG_A)).thenReturn(org);
        when(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).thenReturn(List.of(ORG_A));
        when(userOrgDomainService.findByUserId(TENANT, USER)).thenReturn(List.of(userOrg(ORG_A)));

        try (MockedStatic<TenantContextHolder> tenant = Mockito.mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(TENANT);
            service.setPrimaryOrg(USER, ORG_A);
        }

        InOrder order = inOrder(treeWriteLockSupport, orgTreeConfigDomainService, userOrgDomainService);
        order.verify(treeWriteLockSupport).lockTreeWrites(TENANT, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        order.verify(orgTreeConfigDomainService).resolveDefaultTreeOrgIds(TENANT);
        order.verify(userOrgDomainService).findByUserId(TENANT, USER);
        order.verify(userOrgDomainService).setPrimaryOrgInScope(TENANT, USER, ORG_A, List.of(ORG_A));
    }

    @Test
    @DisplayName("锁前拒绝路径（组织不存在）不触锁不触守卫读（锁不前探到存在性解析）")
    void setPrimaryOrgMissingTargetRejectsBeforeLock() {
        when(orgDomainService.selectValidById(TENANT, ORG_A)).thenReturn(null);

        try (MockedStatic<TenantContextHolder> tenant = Mockito.mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(TENANT);
            org.junit.jupiter.api.Assertions.assertThrows(
                cn.ac.fage.accessmesh.common.exception.BizException.class,
                () -> service.setPrimaryOrg(USER, ORG_A));
        }

        verify(treeWriteLockSupport, never()).lockTreeWrites(any(), any());
        verify(orgTreeConfigDomainService, never()).resolveDefaultTreeOrgIds(any());
        verify(userOrgDomainService, never()).setPrimaryOrgInScope(any(), any(), any(), any());
    }

    private static SysUserOrg userOrg(long orgId) {
        SysUserOrg uo = new SysUserOrg();
        uo.setTenantId(TENANT);
        uo.setUserId(USER);
        uo.setOrgId(orgId);
        return uo;
    }
}
