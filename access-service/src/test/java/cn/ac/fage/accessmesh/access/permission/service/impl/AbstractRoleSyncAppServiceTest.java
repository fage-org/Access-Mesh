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
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
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
    @Mock
    private TreeWriteLockSupport treeWriteLockSupport;
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
                subjectDomainService, treeWriteLockSupport);
        org.mockito.Mockito.lenient().when(syncTypeGuard.validate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        // 版本预判领域判定的 mock 默认：无现存元数据（Map 空取 null）= 新版本放行；
        // 「有现存元数据 = 旧版本」由 stubNewerExistingMetadata 窄化覆盖（真实比较逻辑
        // 由 SyncMetadataDomainServiceImplTest 覆盖）
        org.mockito.Mockito.lenient().when(syncMetadataDomainService.isNewerVersion(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
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
        stubNewerExistingMetadata("org-100");

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 评审修复：旧版本事件无条件按 STALE 钝化（契约：成功 no-op，调度器置 SUCCESS）——
     * 携带非法父边（parent=自身成环）的旧事件不再误报 ROLE_PARENT_INVALID 失败。 */
    @Test
    void shouldReturnStaleForOldCyclicEventInsteadOfFailure() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        stubNewerExistingMetadata("org-200");

        AbstractRoleSyncReq req = new AbstractRoleSyncReq("UPSERT", "BASIC_ROLE", "org-200",
                "Org 200", "BASIC_ROLE", "org-200", "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "200",
                new SyncVersionRef(OCCURRED_AT, 1L));
        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
        org.mockito.Mockito.verifyNoInteractions(abstractRoleMapper);
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
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** T-PERM-022 评审收口：full-sync 通道 parent 环路防护——parent 为目标角色子孙（库内既有
     * 关系构成回边）时该项 nonRetryable 拒绝；写入前逐项判定（当前生效图 = 库内关系 + 本事务
     * 已应用项的边），先于 applyVersion 不推进版本。 */
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

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-200", "BASIC_ROLE", "org-300", 1L))), httpRequest);

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

    /** 评审修复：同一批次两条边共同成环（A→B + B→A）——逐项判定下先应用的安全边落库、
     * 后到闭合环的边拒绝（旧批次预判图实现按库内关系判定双双通过、落下确定性环）。 */
    @Test
    void shouldRejectOnlyCycleClosingItemInTwoNodeBatchCycle() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        stubFullSyncBase(java.util.List.of(a, b), java.util.Map.of("org-a", 7L, "org-b", 8L));

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-a", "BASIC_ROLE", "org-b", 1L),
                item("org-b", "BASIC_ROLE", "org-a", 2L))), httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(1).reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.times(1)).update(any(AbstractRole.class));
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.times(1)).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 评审修复：同一批次三条边成环（A→B + B→C + C→A，三节点）——仅闭合环的末项拒绝。 */
    @Test
    void shouldRejectOnlyCycleClosingItemInThreeNodeBatchCycle() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        AbstractRole c = role(9L, null);
        c.setExternalId("org-c");
        stubFullSyncBase(java.util.List.of(a, b, c),
                java.util.Map.of("org-a", 7L, "org-b", 8L, "org-c", 9L));

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-a", "BASIC_ROLE", "org-b", 1L),
                item("org-b", "BASIC_ROLE", "org-c", 2L),
                item("org-c", "BASIC_ROLE", "org-a", 3L))), httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(2);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(2).reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.times(2)).update(any(AbstractRole.class));
    }

    /** 评审修复：STALE 项不落边保持库内旧边——A→C(STALE) 旧边 A→B 保留，B→A(APPLIED)
     * 对「旧边 + 新边」组合成环须拒绝（旧批次预判图按批内边判定通过、落下 A↔B 环）。 */
    @Test
    void shouldRejectAppliedItemWhenStaleSiblingKeepsOldEdgeFormingCycle() {
        mockHeaderMatch();
        AbstractRole a = role(7L, 8L); // 库内既有 A→B
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(2), any()))
                .thenReturn(java.util.List.of(a, b));
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull()))
                .thenReturn(java.util.Map.of("org-c", 9L, "org-a", 7L));
        when(abstractRoleMapper.selectValidRoleTree(TENANT_ID, false))
                .thenReturn(java.util.List.of(a, b));
        // A 的项 STALE（不落边、保留旧边 A→B）；B 的项走完整链路后判环
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"), eq(SOURCE_SERVICE),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq(businessKey("org-a")),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-a", "BASIC_ROLE", "org-c", 1L),
                item("org-b", "BASIC_ROLE", "org-a", 2L))), httpRequest);

        assertThat(resp.detail().staleCount()).isEqualTo(1);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().appliedCount()).isZero();
        assertThat(resp.detail().itemResults().get(1).reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any(AbstractRole.class));
    }

    /** 评审修复：指向环的前缀安全项放行——X→A、A→B、B→A 中仅 B 被拒（旧实现把前缀
     * 上溯路径上的 X 一并误拒）。 */
    @Test
    void shouldAllowPrefixItemPointingToRejectedCycle() {
        mockHeaderMatch();
        AbstractRole x = role(10L, null);
        x.setExternalId("org-x");
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        stubFullSyncBase(java.util.List.of(x, a, b),
                java.util.Map.of("org-a", 7L, "org-b", 8L));

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-x", "BASIC_ROLE", "org-a", 1L),
                item("org-a", "BASIC_ROLE", "org-b", 2L),
                item("org-b", "BASIC_ROLE", "org-a", 3L))), httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(2);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(0).applied()).isTrue();
        assertThat(resp.detail().itemResults().get(1).applied()).isTrue();
        assertThat(resp.detail().itemResults().get(2).reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.times(2)).update(any(AbstractRole.class));
    }

    /** 评审修复（用户决策对齐 user-role 先例）：同批重复 businessKey 的后续项写入前拒绝。 */
    @Test
    void shouldRejectDuplicateBusinessKeyItems() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        stubFullSyncBase(java.util.List.of(a, b), java.util.Map.of("org-b", 8L));

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-a", "BASIC_ROLE", "org-b", 1L),
                item("org-a", "BASIC_ROLE", "org-a", 2L))), httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(1).reason()).isEqualTo("DUPLICATE_BUSINESS_KEY");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.times(1)).update(any(AbstractRole.class));
    }

    /** 评审修复（契约 §6.2.2.4 既有规则落地）：item 省略 parentRoleTypeCode 时缺省 =
     * scope.roleTypeCode——父解析、判环与写入均按缺省类型走，不再静默丢弃父关系。 */
    @Test
    void shouldDefaultParentTypeToScopeTypeWhenOmitted() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        stubFullSyncBase(java.util.List.of(a, b), java.util.Map.of("org-b", 8L));

        cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem omittedType =
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                        "org-a", "A", null, "org-b", 1, 0, null, "org", "a",
                        new SyncVersionRef(OCCURRED_AT, 1L));
        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(omittedType)), httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        // 父解析按缺省类型（scope 的 BASIC_ROLE）分桶
        verify(typeResolutionService).batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull());
    }

    /** 评审修复：内存图镜像写入语义——已有角色名称-only 更新（未携带父字段）不改库内父，
     * 图须保留旧边，其后 B→A 对真实 A→B 边判环拒绝（旧实现图被误置 null 致漏判落环）。 */
    @Test
    void shouldKeepOldGraphEdgeWhenNameOnlyUpdatePrecedesCycleClose() {
        mockHeaderMatch();
        AbstractRole a = role(7L, 8L); // 库内既有 A→B
        a.setExternalId("org-a");
        AbstractRole b = role(8L, null);
        b.setExternalId("org-b");
        stubFullSyncBase(java.util.List.of(a, b), java.util.Map.of("org-a", 7L));

        cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem nameOnly =
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                        "org-a", "A2", null, null, 1, 0, null, "org", "a",
                        new SyncVersionRef(OCCURRED_AT, 1L));
        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                nameOnly, item("org-b", "BASIC_ROLE", "org-a", 2L))), httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(1).reason()).contains("ROLE_PARENT_INVALID");
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.times(1)).update(any(AbstractRole.class));
    }

    /** 评审修复：full-sync 旧版本环路项保持 STALE 语义（成功 no-op、调度器置 SUCCESS），
     * 不再计入 failed 触发顶层 RETRYABLE（旧实现在版本比较前判环报 ROLE_PARENT_INVALID）。 */
    @Test
    void shouldClassifyOldCyclicItemAsStale() {
        mockHeaderMatch();
        AbstractRole a = role(7L, null);
        a.setExternalId("org-a");
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(2), any()))
                .thenReturn(java.util.List.of(a));
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull()))
                .thenReturn(java.util.Map.of("org-a", 7L));
        stubNewerExistingMetadata("org-a");

        SyncResultResp resp = service.fullSync(TENANT_ID, fullSyncReq(java.util.List.of(
                item("org-a", "BASIC_ROLE", "org-a", 1L))), httpRequest);

        assertThat(resp.detail().staleCount()).isEqualTo(1);
        assertThat(resp.detail().failedCount()).isZero();
        assertThat(resp.detail().itemResults().get(0).stale()).isTrue();
        org.mockito.Mockito.verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any(AbstractRole.class));
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

    /** 测试夹具：带父的 full-sync item */
    private cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem item(
            String externalId, String parentTypeCode, String parentExternalId, long seq) {
        return new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem(
                externalId, externalId, parentTypeCode, parentExternalId, 1, 0, null,
                "org", externalId, new SyncVersionRef(OCCURRED_AT, seq));
    }

    /** 测试夹具：full-sync 请求 */
    private cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq fullSyncReq(
            java.util.List<cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem> items) {
        return new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq(
                new cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncScope(
                        SOURCE_SERVICE, "BASIC_ROLE", "ROOT"), items);
    }

    /** 测试夹具：full-sync 公共桩（类型解析/存量预载/父解析/内存图/版本 APPLIED） */
    private void stubFullSyncBase(java.util.List<AbstractRole> existingRoles,
                                  java.util.Map<String, Long> parentIds) {
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(2), any()))
                .thenReturn(existingRoles);
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), isNull()))
                .thenReturn(parentIds);
        when(abstractRoleMapper.selectValidRoleTree(TENANT_ID, false)).thenReturn(existingRoles);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"), eq(SOURCE_SERVICE),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
    }

    /** 测试夹具：角色业务键（与实现 SyncKeyCodec 同构） */
    private String businessKey(String externalId) {
        return cn.ac.fage.accessmesh.access.permission.util.SyncKeyCodec
                .abstractRoleBusinessKey("BASIC_ROLE", externalId);
    }

    /** 测试夹具：预载「现存版本严格更新」的元数据（使该角色的事件按 STALE 钝化） */
    private void stubNewerExistingMetadata(String externalId) {
        String hash = cn.ac.fage.accessmesh.access.permission.util.SyncKeyCodec
                .sha256Hex(businessKey(externalId));
        cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata meta =
                new cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata();
        meta.setBusinessKeyHash(hash);
        meta.setLastSyncOccurredAt(OCCURRED_AT.plusSeconds(1));
        meta.setLastSyncSequenceNo(999L);
        when(syncMetadataDomainService.mapByBusinessKeyHash(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(java.util.Map.of(hash, meta));
        when(syncMetadataDomainService.isNewerVersion(
                org.mockito.ArgumentMatchers.<cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata>argThat(
                        m -> m != null),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(false);
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
