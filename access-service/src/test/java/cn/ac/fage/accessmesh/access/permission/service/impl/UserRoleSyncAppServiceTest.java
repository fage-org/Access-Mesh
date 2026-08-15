package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncScope;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserRoleSyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleSyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private UserRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, userRoleMapper, new LocalProjectionGuard());
    }

    private UserRoleSyncReq bindReq() {
        return new UserRoleSyncReq("BIND", "SYS_USER_ORG",
                "USER", "u1", "ORG", "1", "org-100", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    @Test
    void shouldRejectReservedSysUserOrgSource_whenBind() {
        mockHeaderMatch();
        assertThatThrownBy(() -> service.sync(TENANT_ID, bindReq(), httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        SyncResultResp resp = service.sync(TENANT_ID, bindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("SOURCE_SERVICE_MISMATCH");
    }

    @Test
    void shouldReturnNonRetryable_whenSourceTypeNotSysUserOrg() {
        mockHeaderMatch();
        UserRoleSyncReq req = new UserRoleSyncReq("BIND", "OTHER_SOURCE",
                "USER", "u1", "ORG", "1", "org-100", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("INVALID_USER_ROLE_SOURCE_OR_TYPE");
    }

    @Test
    void shouldRejectReservedSysUserOrgSource_whenFullSync() {
        mockHeaderMatch();
        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "SYS_USER_ORG", "ORG", "1"),
                List.of(new UserRoleSyncItem(
                        "USER", "u1",
                        "ORG", "org-1",
                        "ORG:org-1",
                        null, null, "user", "u1",
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        assertThatThrownBy(() -> service.fullSync(TENANT_ID, req, httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }
}
