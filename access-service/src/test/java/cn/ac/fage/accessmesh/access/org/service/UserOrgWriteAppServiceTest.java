package cn.ac.fage.accessmesh.access.org.service;

import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.impl.UserOrgWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * removeUserFromOrg 默认树最后归属保护回归锁（T-ORG-002）。
 * <p>
 * 最后归属判定已换绑共享守卫 {@link OrgTreeConfigDomainService#findUsersLosingDefaultHome}
 * （原 AppService 内联过滤，与组织删除级联/树配置切根同语义，避免平行守卫漂移）。
 * 本测试锁定换绑后行为不变：默认树内失去最后归属仍拒绝 11013（原 message）、
 * 有其他归属放行、非默认树走 ORG 门禁分支不做归属判定。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserOrgWriteAppServiceTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Long USER_ID = 100L;
    private static final Long ORG_ID = 10L;

    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

    private UserOrgWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserOrgWriteAppServiceImpl(userOrgDomainService, orgTreeConfigDomainService,
            orgDomainService, permissionValidator, localProjectionDomainService, auditDomainService,
            treeWriteLockSupport);
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    private SysOrg org(Long id, String orgType) {
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setTenantId(TENANT);
        org.setOrgType(orgType);
        org.setCode("DEV");
        org.setParentId(1L);
        return org;
    }

    @Test
    @DisplayName("默认树内移除且用户将失去最后归属 → 拒绝（USER_LOSE_DEFAULT_TREE_HOME 原 message）")
    void removeLastDefaultHomeRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org(ORG_ID, "1"));
        when(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).thenReturn(List.of(1L, ORG_ID));
        when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT), any(), any()))
            .thenReturn(Set.of(USER_ID));

        assertThatThrownBy(() -> service.removeUserFromOrg(USER_ID, ORG_ID))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("移除后用户在默认组织树无归属关系");
        verify(userOrgDomainService, never()).deleteByUserIdAndOrgId(anyLong(), anyLong(), anyLong());
        // 守卫读与删除均在 SYS_ORG 树锁内（claude 外评 P2：与组织结构写/树配置守卫串行）
        verify(treeWriteLockSupport).lockTreeWrites(TENANT,
            TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
    }

    @Test
    @DisplayName("默认树内移除且用户仍有其他默认树归属 → 放行删除关系")
    void removeWithRetainedDefaultHomeAllowed() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org(ORG_ID, "1"));
        when(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).thenReturn(List.of(1L, ORG_ID));
        when(orgTreeConfigDomainService.findUsersLosingDefaultHome(eq(TENANT), any(), any()))
            .thenReturn(Set.of());
        when(localProjectionDomainService.findAdminUserId(TENANT, USER_ID)).thenReturn(null);
        when(localProjectionDomainService.unbindUserOrg(eq(TENANT), eq(USER_ID), eq(ORG_ID), eq("ORG"), any()))
            .thenReturn(null);

        service.removeUserFromOrg(USER_ID, ORG_ID);

        verify(userOrgDomainService).deleteByUserIdAndOrgId(TENANT, USER_ID, ORG_ID);
    }

    @Test
    @DisplayName("非默认树组织移除 → 走 ORG 实例门禁分支，不做默认树归属判定")
    void removeNonDefaultTreeOrgSkipsDefaultHomeGuard() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org(ORG_ID, "2"));
        when(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).thenReturn(List.of(1L));
        when(localProjectionDomainService.findAdminUserId(TENANT, USER_ID)).thenReturn(null);
        when(localProjectionDomainService.unbindUserOrg(eq(TENANT), eq(USER_ID), eq(ORG_ID), eq("POSITION"), any()))
            .thenReturn(null);

        service.removeUserFromOrg(USER_ID, ORG_ID);

        verify(orgTreeConfigDomainService, never()).findUsersLosingDefaultHome(anyLong(), any(), any());
        verify(userOrgDomainService).deleteByUserIdAndOrgId(TENANT, USER_ID, ORG_ID);
    }

    @Test
    @DisplayName("目标组织不存在 → 拒绝（ORG_NOT_FOUND）")
    void removeWithMissingOrgRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.removeUserFromOrg(USER_ID, ORG_ID))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("org not found");
    }
}
