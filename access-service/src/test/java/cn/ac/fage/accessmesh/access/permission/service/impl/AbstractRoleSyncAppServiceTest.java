package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AbstractRoleSyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AbstractRoleSyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private AbstractRoleMapper abstractRoleMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private SyncTypeGuard syncTypeGuard;
    @Mock
    private cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService subjectDomainService;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private AbstractRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AbstractRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, abstractRoleMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard,
                subjectDomainService);
        org.mockito.Mockito.lenient().when(syncTypeGuard.validate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    /**
     * UPSERT 请求，无 parent。
     */
    private AbstractRoleSyncReq upsertReqNoParent() {
        return new AbstractRoleSyncReq("UPSERT", "BASIC_ROLE", "org-100",
                "Org 100", null, null, "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "100",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    /**
     * UPSERT 请求，带 parent。
     */
    private AbstractRoleSyncReq upsertReqWithParent() {
        return new AbstractRoleSyncReq("UPSERT", "BASIC_ROLE", "org-200",
                "Org 200", "BASIC_ROLE", "org-100", "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "200",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    @Test
    void shouldReturnApplied_whenUpsertNewVersion() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT_ID, 2, "org-100")).thenReturn(null);
        lenient().when(abstractRoleMapper.insert(any(AbstractRole.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();
        assertThat(resp.retryClass()).isNull();
    }

    @Test
    void shouldReturnStale_whenVersionStale() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
    }

    @Test
    void shouldReturnDependencyMissing_whenParentRoleNotFound() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "BASIC_ROLE", "org-100", null)).thenReturn(null);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqWithParent(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_DEPENDENCY_MISSING);
        assertThat(resp.reason()).contains("PARENT_ROLE_NOT_FOUND");
    }

    @Test
    void shouldRejectReservedOrgRoleType() {
        mockHeaderMatch();
        AbstractRoleSyncReq req = new AbstractRoleSyncReq("UPSERT", "ORG", "org-100",
                "Org 100", null, null, "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "100",
                new SyncVersionRef(OCCURRED_AT, 1L));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
    }

    /** T-PERM-043：sync 通道与通用 create/update 同口径拒绝 GROUP_ROLE（20022）。 */
    @Test
    void shouldRejectGroupRoleTypeOnSync() {
        mockHeaderMatch();
        AbstractRoleSyncReq req = new AbstractRoleSyncReq("UPSERT", "GROUP_ROLE", "group-100",
                "Group 100", null, null, "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "group", "100",
                new SyncVersionRef(OCCURRED_AT, 1L));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting(ex -> ((cn.ac.fage.accessmesh.common.exception.BizException) ex).getErrorCode())
                .isEqualTo(cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode());
    }

    /** T-PERM-043：full-sync 通道同口径拒绝 GROUP_ROLE（20022），不落任何同步事实。 */
    @Test
    void shouldRejectGroupRoleTypeOnFullSync() {
        mockHeaderMatch();
        cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq req =
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq(
                        new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncScope(
                                SOURCE_SERVICE, "GROUP_ROLE", "ROOT"),
                        java.util.List.of(new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                "group-100", "Group 100", null, null, 1, 0, null,
                                "group", "100", new SyncVersionRef(OCCURRED_AT, 1L))));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.fullSync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting(ex -> ((cn.ac.fage.accessmesh.common.exception.BizException) ex).getErrorCode())
                .isEqualTo(cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode());
        org.mockito.Mockito.verifyNoInteractions(syncMetadataDomainService);
        org.mockito.Mockito.verifyNoInteractions(abstractRoleMapper);
    }

    /** T-PERM-022 评审收口：sync 通道 parent 环路防护——parent 指向角色自身时 nonRetryable 拒绝，
     * 不落事实变更也不推进同步版本（判环先于 applyVersion，上游修正后同版本重试不被判 STALE）。 */
    @Test
    void shouldRejectSyncWhenParentIsRoleItself() {
        mockHeaderMatch();
        AbstractRole existing = new AbstractRole();
        existing.setId(7L);
        existing.setTenantId(TENANT_ID);
        existing.setRoleType(2);
        existing.setExternalId("org-200");
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "BASIC_ROLE", "org-200", null)).thenReturn(7L);
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT_ID, 2, "org-200")).thenReturn(existing);

        AbstractRoleSyncReq req = new AbstractRoleSyncReq("UPSERT", "BASIC_ROLE", "org-200",
                "Org 200", "BASIC_ROLE", "org-200", "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "200",
                new SyncVersionRef(OCCURRED_AT, 1L));
        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any(AbstractRole.class));
        org.mockito.Mockito.verifyNoInteractions(syncMetadataDomainService);
    }

    /** T-PERM-022 评审收口：full-sync 通道 parent 环路防护——parent 为目标角色子孙（库内既有
     * 关系构成回边）时该项 nonRetryable 拒绝；最终图判环 = 库内父子关系 + 批内待更新边。 */
    @Test
    void shouldRejectFullSyncItemWhenParentIsDescendant() {
        mockHeaderMatch();
        AbstractRole existing = role(7L, null);
        existing.setExternalId("org-200");
        AbstractRole child = role(8L, 7L);
        child.setExternalId("org-300");
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(2), any()))
                .thenReturn(java.util.List.of(existing));
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull()))
                .thenReturn(java.util.Map.of("org-300", 8L));
        when(abstractRoleMapper.selectValidRoleTree(TENANT_ID, false))
                .thenReturn(java.util.List.of(existing, child));

        cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq req =
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq(
                        new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncScope(
                                SOURCE_SERVICE, "BASIC_ROLE", "ROOT"),
                        java.util.List.of(new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                "org-200", "Org 200", "BASIC_ROLE", "org-300", 1, 0, null,
                                "org", "200", new SyncVersionRef(OCCURRED_AT, 1L))));
        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(0).retryClass())
                .isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.detail().itemResults().get(0).reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any(AbstractRole.class));
        // 版本未推进：applyVersion 从未被调用（尾部差异校准 listScopeForFullSync 属正常只读交互）
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 评审修复：同一批次两条边共同成环（A→B + B→A）——旧实现按库内关系分别判定均通过、
     * 阶段 C 落下确定性双节点环；最终图判环下两参与项均拒绝。 */
    @Test
    void shouldRejectFullSyncBatchFormingTwoNodeCycle() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(2), any()))
                .thenReturn(java.util.List.of(a, b));
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull()))
                .thenReturn(java.util.Map.of("org-a", 7L, "org-b", 8L));
        when(abstractRoleMapper.selectValidRoleTree(TENANT_ID, false))
                .thenReturn(java.util.List.of(a, b));

        cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq req =
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq(
                        new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncScope(
                                SOURCE_SERVICE, "BASIC_ROLE", "ROOT"),
                        java.util.List.of(
                                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                        "org-a", "A", "BASIC_ROLE", "org-b", 1, 0, null,
                                        "org", "a", new SyncVersionRef(OCCURRED_AT, 1L)),
                                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                        "org-b", "B", "BASIC_ROLE", "org-a", 1, 0, null,
                                        "org", "b", new SyncVersionRef(OCCURRED_AT, 2L))));
        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.detail().failedCount()).isEqualTo(2);
        assertThat(resp.detail().itemResults()).allSatisfy(r -> {
            assertThat(r.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
            assertThat(r.reason()).contains("ROLE_PARENT_INVALID");
        });
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any(AbstractRole.class));
        // 版本未推进：applyVersion 从未被调用（尾部差异校准 listScopeForFullSync 属正常只读交互）
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 评审修复：同一批次三条边成环（A→B + B→C + C→A，三节点）。 */
    @Test
    void shouldRejectFullSyncBatchFormingThreeNodeCycle() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        AbstractRole c = role(9L, null);
        c.setExternalId("org-c");
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(2), any()))
                .thenReturn(java.util.List.of(a, b, c));
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull()))
                .thenReturn(java.util.Map.of("org-a", 7L, "org-b", 8L, "org-c", 9L));
        when(abstractRoleMapper.selectValidRoleTree(TENANT_ID, false))
                .thenReturn(java.util.List.of(a, b, c));

        cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq req =
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq(
                        new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncScope(
                                SOURCE_SERVICE, "BASIC_ROLE", "ROOT"),
                        java.util.List.of(
                                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                        "org-a", "A", "BASIC_ROLE", "org-b", 1, 0, null,
                                        "org", "a", new SyncVersionRef(OCCURRED_AT, 1L)),
                                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                        "org-b", "B", "BASIC_ROLE", "org-c", 1, 0, null,
                                        "org", "b", new SyncVersionRef(OCCURRED_AT, 2L)),
                                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                                        "org-c", "C", "BASIC_ROLE", "org-a", 1, 0, null,
                                        "org", "c", new SyncVersionRef(OCCURRED_AT, 3L))));
        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.detail().failedCount()).isEqualTo(3);
        assertThat(resp.detail().itemResults()).allSatisfy(r -> {
            assertThat(r.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
            assertThat(r.reason()).contains("ROLE_PARENT_INVALID");
        });
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any(AbstractRole.class));
        // 版本未推进：applyVersion 从未被调用（尾部差异校准 listScopeForFullSync 属正常只读交互）
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 测试夹具：BASIC_ROLE(roleType=2) 角色行 */
    private AbstractRole role(Long id, Long parentId) {
        AbstractRole r = new AbstractRole();
        r.setId(id);
        r.setTenantId(TENANT_ID);
        r.setRoleType(2);
        r.setParentId(parentId);
        r.setStatus(1);
        return r;
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
    }

    @Test
    void shouldBackfillTargetId_whenUpsertCreatesNewAbstractRole() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT_ID, 2, "org-100")).thenReturn(null);
        // 模拟 mapper.insert 设置 id
        when(abstractRoleMapper.insert(any(AbstractRole.class))).thenAnswer(inv -> {
            AbstractRole r = inv.getArgument(0);
            r.setId(9999L);
            return 1;
        });

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(syncMetadataDomainService).backfillTargetId(
                eq(TENANT_ID), eq("ABSTRACT_ROLE"), eq(SOURCE_SERVICE),
                anyString(), anyString(), eq(9999L));
    }
}
