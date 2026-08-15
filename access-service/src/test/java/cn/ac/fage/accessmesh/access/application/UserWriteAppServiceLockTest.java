package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.security.OrgVisibilityService;
import cn.ac.fage.accessmesh.access.application.impl.UserWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-005 评审 P1（用户决策：同步禁用投影）：登录失败锁定走 UserWriteAppService.lockUser
 * 内部编排——同一事务更新 sys_user.status=2 + 禁用投影 + change_log + markUsers。
 */
@ExtendWith(MockitoExtension.class)
class UserWriteAppServiceLockTest {

    private static final Long TENANT = 1L;
    private static final Long USER_ID = 42L;

    @Mock private UserDomainService userDomainService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgVisibilityService orgVisibilityService;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;

    private UserWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserWriteAppServiceImpl(
            userDomainService,
            userOrgDomainService,
            orgTreeConfigDomainService,
            orgDomainService,
            permissionValidator,
            orgVisibilityService,
            localProjectionDomainService,
            auditDomainService,
            new ObjectMapper()
        );
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.anonymous());
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("lockUser：更新 status=2 + 禁用投影 + change_log（操作者为 null，匿名登录路径安全）")
    void lockUserUpdatesFactDisablesProjectionAndWritesChangeLog() {
        when(localProjectionDomainService.findAdminUserId(TENANT, USER_ID)).thenReturn(501L);

        service.lockUser(TENANT, USER_ID);

        verify(userDomainService).batchUpdateStatus(TENANT, List.of(USER_ID), 2);
        verify(localProjectionDomainService).disableAdminUser(TENANT, USER_ID);
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    @Test
    @DisplayName("lockUser：投影不存在时仍更新事实，跳过 change_log 与 markUsers")
    void lockUserWithoutProjectionStillUpdatesFact() {
        when(localProjectionDomainService.findAdminUserId(TENANT, USER_ID)).thenReturn(null);

        service.lockUser(TENANT, USER_ID);

        verify(userDomainService).batchUpdateStatus(TENANT, List.of(USER_ID), 2);
        verify(localProjectionDomainService).disableAdminUser(TENANT, USER_ID);
        verify(auditDomainService, never()).recordChangeLog(any(), any());
    }

    @Test
    @DisplayName("lockUser：匿名上下文（无操作者）不抛异常（T-ACCESS-005 评审 P1 回归保护）")
    void lockUserWorksWithoutOperatorContext() {
        when(localProjectionDomainService.findAdminUserId(TENANT, USER_ID)).thenReturn(501L);

        service.lockUser(TENANT, USER_ID);

        // 匿名上下文：AccessRequestContext operatorId=null，StpUtil 未登录也不应被调用
        assertThat(AccessRequestContext.getOperatorId()).isNull();
        verify(auditDomainService).recordChangeLog(any(), any());
    }
}
