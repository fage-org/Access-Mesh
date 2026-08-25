package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.impl.UserWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.application.query.OrgVisibilityQueryService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /user/create 的 status 0/1 收口单测（T-ADMIN-022 评审修复：create 是唯一漏校验的
 * sys_user.status 写入口——status=2 可致「认证放行 + 投影停用」事实分裂）。
 */
@ExtendWith(MockitoExtension.class)
class UserWriteAppServiceCreateStatusTest {

    private static final Long TENANT = 1L;

    @Mock private UserDomainService userDomainService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgVisibilityQueryService orgVisibilityQueryService;
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
            orgVisibilityQueryService,
            localProjectionDomainService,
            auditDomainService,
            new ObjectMapper()
        );
        // 绑定带操作者的用户上下文：operatorId 非 null 时 currentOperatorId 跳过 StpUtil 兜底（纯单测无 Sa-Token 上下文）
        AccessRequestContext.bind(RequestContext.user(TENANT, 900L));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    private UserCreateReq req(Integer status) {
        return new UserCreateReq("carol", "评审测试用户", null, null, status, null, null);
    }

    @Test
    @DisplayName("status=2 拒绝（10008）且不产生任何写（用户名查重都不触发）")
    void createRejectsStatusOutsideZeroAndOne() {
        assertThatThrownBy(() -> service.createUser(req(2)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .hasMessageContaining("0(停用)或1(启用)");

        verify(userDomainService, never()).existsByUsername(anyLong(), anyString());
        verify(userDomainService, never()).insert(any(SysUser.class));
        verify(localProjectionDomainService, never()).createLocalUserSubject(
            anyLong(), anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("status=0 合法：走完创建流程，投影 enabled=false 与事实同源")
    void createWithDisabledStatusKeepsFactAndProjectionAligned() {
        when(userDomainService.existsByUsername(TENANT, "carol")).thenReturn(false);
        when(localProjectionDomainService.createLocalUserSubject(
            anyLong(), anyString(), anyBoolean(), anyString())).thenReturn(601L);

        UserCreateResp resp = service.createUser(req(0));

        assertThat(resp.id()).isEqualTo(601L);
        ArgumentCaptor<Boolean> enabled = ArgumentCaptor.forClass(Boolean.class);
        verify(localProjectionDomainService).createLocalUserSubject(
            anyLong(), anyString(), enabled.capture(), anyString());
        assertThat(enabled.getValue()).as("status=0 投影必须同步停用").isFalse();
        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(userDomainService).insert(user.capture());
        assertThat(user.getValue().getStatus()).isEqualTo(0);
    }
}
