package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.user.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.user.service.impl.UserWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
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
    @Mock private OrgVisibilityQueryAppService orgVisibilityQueryService;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

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
            treeWriteLockSupport
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
            .hasMessageContaining("0(停用)或1(启用)")
            .extracting("errorCode", org.assertj.core.api.InstanceOfAssertFactories.INTEGER)
            .isEqualTo(10008);

        verify(userDomainService, never()).existsByUsername(anyLong(), anyString());
        verify(userDomainService, never()).insert(any(SysUser.class));
        verify(localProjectionDomainService, never()).createLocalUserSubject(
            anyLong(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("status=0 合法：走完创建流程，投影 enabled=false 与事实同源")
    void createWithDisabledStatusKeepsFactAndProjectionAligned() {
        when(userDomainService.existsByUsername(TENANT, "carol")).thenReturn(false);
        when(localProjectionDomainService.createLocalUserSubject(
            anyLong(), anyString(), anyBoolean())).thenReturn(601L);

        UserCreateResp resp = service.createUser(req(0));

        assertThat(resp.id()).isEqualTo(601L);
        ArgumentCaptor<Boolean> enabled = ArgumentCaptor.forClass(Boolean.class);
        verify(localProjectionDomainService).createLocalUserSubject(
            anyLong(), anyString(), enabled.capture());
        assertThat(enabled.getValue()).as("status=0 投影必须同步停用").isFalse();
        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(userDomainService).insert(user.capture());
        assertThat(user.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("带 orgId 创建用户：SYS_ORG 树锁先于默认树范围首读（外评 R4，旧实现必红）")
    void createUserWithOrgIdLocksTreeBeforeDefaultTreeRead() {
        when(userDomainService.existsByUsername(TENANT, "carol")).thenReturn(false);
        when(localProjectionDomainService.createLocalUserSubject(
            anyLong(), anyString(), anyBoolean())).thenReturn(601L);
        when(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).thenReturn(List.of(50L));
        when(orgDomainService.selectValidById(TENANT, 50L)).thenReturn(new SysOrg());

        service.createUser(new UserCreateReq("carol", "评审测试用户", null, null, 1, 50L, null));

        // 默认树范围读取（validateOrgInDefaultTree→resolveDefaultTreeOrgIds）必须在 SYS_ORG
        // 树锁之后：否则与并发 setDefault 的归属守卫交错（守卫看不到未提交的新成员关系），
        // 切树放行后新用户只落在旧树——创建成功却不在默认身份目录
        InOrder inOrder = inOrder(treeWriteLockSupport, orgTreeConfigDomainService);
        inOrder.verify(treeWriteLockSupport)
            .lockTreeWrites(TENANT, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        inOrder.verify(orgTreeConfigDomainService).resolveDefaultTreeOrgIds(TENANT);
    }

    @Test
    @DisplayName("不带 orgId 创建用户：不取 SYS_ORG 树锁（无成员关系写，锁面不扩大）")
    void createUserWithoutOrgIdSkipsTreeLock() {
        when(userDomainService.existsByUsername(TENANT, "carol")).thenReturn(false);
        when(localProjectionDomainService.createLocalUserSubject(
            anyLong(), anyString(), anyBoolean())).thenReturn(601L);

        service.createUser(new UserCreateReq("carol", "评审测试用户", null, null, 1, null, null));

        verify(treeWriteLockSupport, never()).lockTreeWrites(anyLong(), any());
    }
}
