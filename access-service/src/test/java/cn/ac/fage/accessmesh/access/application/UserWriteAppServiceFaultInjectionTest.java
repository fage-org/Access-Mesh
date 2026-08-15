package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
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
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-005 故障注入：管理事实、权限投影、permission_change_log 任一步失败则方法失败。
 * 编排层标注 {@code @Transactional(rollbackFor = Exception.class)}，异常即整体回滚。
 */
@ExtendWith(MockitoExtension.class)
class UserWriteAppServiceFaultInjectionTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;

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
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("投影失败时不写 change_log，异常向上抛出以便事务回滚")
    void projectionFailureStopsChangeLog() {
        when(userDomainService.existsByUsername(TENANT, "alice")).thenReturn(false);
        doAnswer(inv -> {
            SysUser user = inv.getArgument(0);
            user.setId(101L);
            return null;
        }).when(userDomainService).insert(any(SysUser.class));
        when(localProjectionDomainService.upsertAdminUser(anyLong(), anyLong(), anyString(), anyBoolean(), any()))
            .thenThrow(new SystemException(90001, "projection failed"));

        assertThatThrownBy(() -> service.createUser(createReq()))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("projection failed");

        verify(userDomainService).insert(any(SysUser.class));
        verify(auditDomainService, never()).recordChangeLog(any(), any());
    }

    @Test
    @DisplayName("permission_change_log 失败时异常向上抛出以便事务回滚")
    void changeLogFailurePropagates() {
        when(userDomainService.existsByUsername(TENANT, "alice")).thenReturn(false);
        doAnswer(inv -> {
            SysUser user = inv.getArgument(0);
            user.setId(101L);
            return null;
        }).when(userDomainService).insert(any(SysUser.class));
        when(localProjectionDomainService.upsertAdminUser(anyLong(), anyLong(), anyString(), anyBoolean(), any()))
            .thenReturn(501L);
        doThrow(new SystemException(90001, "change log failed"))
            .when(auditDomainService).recordChangeLog(any(), any());

        assertThatThrownBy(() -> service.createUser(createReq()))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("change log failed");

        verify(localProjectionDomainService).upsertAdminUser(anyLong(), anyLong(), anyString(), anyBoolean(), any());
    }

    @Test
    @DisplayName("管理事实插入失败时不写投影")
    void factInsertFailureSkipsProjection() {
        when(userDomainService.existsByUsername(TENANT, "alice")).thenReturn(false);
        doThrow(new BizException(10120, "insert failed")).when(userDomainService).insert(any(SysUser.class));

        assertThatThrownBy(() -> service.createUser(createReq()))
            .isInstanceOf(BizException.class);

        verify(localProjectionDomainService, never())
            .upsertAdminUser(anyLong(), anyLong(), anyString(), anyBoolean(), any());
        verify(auditDomainService, never()).recordChangeLog(any(), any());
    }

    private static UserCreateReq createReq() {
        return new UserCreateReq("alice", "Alice", null, null, 1, null, null);
    }
}
