package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserSyncScope;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.util.SyncKeyCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3 回归测试：full-sync 移除项之后，必须 markStatus(DELETED) 而非 softDelete sync_metadata；
 * 这样旧版本事件抵达时仍有比较基线，applyVersion 能返回 STALE，避免旧版本数据复活。
 *
 * <p>覆盖：
 * <ul>
 *   <li>用例 A（核心防回归）：full-sync 删除 → 旧版本单次 sync 抵达 → STALE，业务表保持已删除。</li>
 *   <li>用例 B：full-sync 删除 → 新版本单次 sync 抵达 → markStatus(ACTIVE) 重新激活。</li>
 *   <li>用例 C（健全性）：full-sync 删除路径不调用 syncMetadataMapper.softDelete；
 *       而是调用 markStatus(DELETED) + 业务表 softDeleteBatch。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FullSyncStaleVersionTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final String ENTITY_KIND = "ABSTRACT_USER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";

    /**
     * full-sync 发生在 t0；之后旧版本 sync 事件 occurredAt 早于 t0，
     * 新版本 sync 事件 occurredAt 晚于 t0。
     */
    private static final LocalDateTime T_FULLSYNC = LocalDateTime.of(2026, 6, 1, 12, 0);
    private static final LocalDateTime T_OLD = T_FULLSYNC.minusHours(1);
    private static final LocalDateTime T_NEW = T_FULLSYNC.plusHours(1);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private AbstractUserMapper abstractUserMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private SyncTypeGuard syncTypeGuard;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private AbstractUserSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AbstractUserSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, abstractUserMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        org.mockito.Mockito.lenient().when(syncTypeGuard.validate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
        lenient().when(typeResolutionService.resolveTypeValue(TENANT_ID, "user_type", "USER")).thenReturn(0);
    }

    /**
     * 用例 A：full-sync 不再带 "u-removed" → markStatus(DELETED) + 软删 abstract_user。
     * 接到旧版本（occurredAt 早于 last_sync_occurred_at）的单次 sync 事件 → applyVersion=STALE，
     * 业务表 softDeleteBatch 不再被调用，保持已删除。
     */
    @Test
    void shouldReturnStale_whenOldVersionArrivesAfterFullSyncDeletion() {
        // ---- step 1: full-sync 移除 u-removed ----
        // applyVersion 对仍存在的 u-keep 项返回 APPLIED
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(T_FULLSYNC), eq(10L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractUserMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(0), any()))
                .thenReturn(List.of(buildUser(100L, "u-keep")));
        // 单条 sync 路径仍可能调用单条 select；保留为 lenient 防 UnnecessaryStubbing
        lenient().when(abstractUserMapper.selectByTypeAndExternalId(TENANT_ID, 0, "u-keep"))
                .thenReturn(buildUser(100L, "u-keep"));

        // listScopeForFullSync 返回包含 u-keep 与 u-removed 两条 metadata
        SyncMetadata mdKeep = buildMetadata(100L, "u-keep", STATUS_ACTIVE, T_FULLSYNC.minusDays(1), 1L);
        SyncMetadata mdRemoved = buildMetadata(200L, "u-removed", STATUS_ACTIVE, T_FULLSYNC.minusDays(1), 1L);
        when(syncMetadataDomainService.listScopeForFullSync(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString()))
                .thenReturn(List.of(mdKeep, mdRemoved));

        AbstractUserFullSyncReq fullReq = new AbstractUserFullSyncReq(
                new AbstractUserSyncScope(SOURCE_SERVICE, "USER"),
                List.of(new AbstractUserSyncItem("u-keep", "Keep", true, null,
                        null, null, new SyncVersionRef(T_FULLSYNC, 10L)))
        );

        SyncResultResp fullResp = service.fullSync(TENANT_ID, fullReq, httpRequest);
        assertThat(fullResp.accepted()).isTrue();

        // 关键断言：u-removed 走 markStatus(DELETED)，并对业务表 softDeleteBatch
        verify(syncMetadataDomainService).markStatus(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), eq(mdRemoved.getBusinessKeyHash()), eq(STATUS_DELETED));
        verify(abstractUserMapper).softDeleteBatch(eq(TENANT_ID), eq(List.of(200L)), any(LocalDateTime.class));

        // ---- step 2: 旧版本 sync 事件 (occurredAt < T_FULLSYNC) 抵达 ----
        // 清空 step 1 的调用记录，专注断言"旧版本不复活"语义
        clearInvocations(syncMetadataDomainService, abstractUserMapper);

        // applyVersion 拿到旧版本 → STALE（因为 sync_metadata 仍在，比较基线没丢）
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(T_OLD), eq(5L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        AbstractUserSyncReq oldReq = new AbstractUserSyncReq("UPSERT", "USER", "u-removed",
                "Resurrected User", Boolean.TRUE, null,
                SOURCE_SERVICE, null, null,
                new SyncVersionRef(T_OLD, 5L));

        SyncResultResp oldResp = service.sync(TENANT_ID, oldReq, httpRequest);

        // 旧版本必须 STALE，业务表不能再被插入/更新而复活
        assertThat(oldResp.accepted()).isTrue();
        assertThat(oldResp.applied()).isFalse();
        assertThat(oldResp.stale()).isTrue();
        assertThat(oldResp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
        verify(abstractUserMapper, never()).insert(any(AbstractUser.class));
        verify(abstractUserMapper, never()).update(any(AbstractUser.class));
        verify(abstractUserMapper, never()).softDeleteBatch(eq(TENANT_ID), any(), any(LocalDateTime.class));
        // markStatus 在 STALE 路径不应被调用（applyVersion 提前返回）
        verify(syncMetadataDomainService, never()).markStatus(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString());
    }

    /**
     * 用例 B：full-sync 移除 u-removed 后，新版本（occurredAt > last）的单次 sync 抵达 →
     * applyVersion=APPLIED，markStatus(ACTIVE) 重新激活；abstract_user 表新插或更新。
     */
    @Test
    void shouldReactivate_whenNewVersionArrivesAfterFullSyncDeletion() {
        // 直接进入 step 2（用例 A 已覆盖 full-sync 阶段；本用例聚焦于"复活"语义）
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(T_NEW), eq(20L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        // u-removed 在业务表中目前是软删状态，selectByTypeAndExternalId 不返回它（DELETE_FLAG=0 过滤）
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT_ID, 0, "u-removed")).thenReturn(null);
        when(abstractUserMapper.insert(any(AbstractUser.class))).thenAnswer(inv -> {
            AbstractUser u = inv.getArgument(0);
            u.setId(201L);
            return 1;
        });

        AbstractUserSyncReq newReq = new AbstractUserSyncReq("UPSERT", "USER", "u-removed",
                "Resurrected User", Boolean.TRUE, null,
                SOURCE_SERVICE, null, null,
                new SyncVersionRef(T_NEW, 20L));

        SyncResultResp resp = service.sync(TENANT_ID, newReq, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();

        // markStatus(ACTIVE) 必须被调用（重新激活 sync_metadata）
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(syncMetadataDomainService).markStatus(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(STATUS_ACTIVE);

        // 业务表插入了新行（targetId=201）
        verify(abstractUserMapper, times(1)).insert(any(AbstractUser.class));
        verify(syncMetadataDomainService).backfillTargetId(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), eq(201L));
    }

    /**
     * 用例 C（健全性）：full-sync 删除分支只走 markStatus(DELETED) + abstractUserMapper.softDeleteBatch；
     * 不能再触碰 syncMetadataDomainService 上任何"软删元数据"的方法（接口已没有这个方法，
     * 这里通过 markStatus 调用次数和参数白名单兜底）。
     */
    @Test
    void shouldNotSoftDeleteSyncMetadata_inFullSyncDeactivation() {
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(T_FULLSYNC), eq(10L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractUserMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(0), any()))
                .thenReturn(List.of(buildUser(100L, "u-keep")));
        // 单条 sync 路径仍可能调用单条 select；保留为 lenient 防 UnnecessaryStubbing
        lenient().when(abstractUserMapper.selectByTypeAndExternalId(TENANT_ID, 0, "u-keep"))
                .thenReturn(buildUser(100L, "u-keep"));

        SyncMetadata mdKeep = buildMetadata(100L, "u-keep", STATUS_ACTIVE, T_FULLSYNC.minusDays(1), 1L);
        SyncMetadata mdRemoved = buildMetadata(200L, "u-removed", STATUS_ACTIVE, T_FULLSYNC.minusDays(1), 1L);
        // 已经是 DELETED 状态的不应被重复处理
        SyncMetadata mdAlreadyDeleted = buildMetadata(300L, "u-old", STATUS_DELETED, T_FULLSYNC.minusDays(2), 1L);
        when(syncMetadataDomainService.listScopeForFullSync(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString()))
                .thenReturn(List.of(mdKeep, mdRemoved, mdAlreadyDeleted));

        AbstractUserFullSyncReq fullReq = new AbstractUserFullSyncReq(
                new AbstractUserSyncScope(SOURCE_SERVICE, "USER"),
                List.of(new AbstractUserSyncItem("u-keep", "Keep", true, null,
                        null, null, new SyncVersionRef(T_FULLSYNC, 10L)))
        );

        service.fullSync(TENANT_ID, fullReq, httpRequest);

        // 只有 u-removed 应该走 DELETED；u-old 已是 DELETED，跳过
        verify(syncMetadataDomainService).markStatus(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), eq(mdRemoved.getBusinessKeyHash()), eq(STATUS_DELETED));
        verify(syncMetadataDomainService, never()).markStatus(eq(TENANT_ID), eq(ENTITY_KIND),
                eq(SOURCE_SERVICE), anyString(), eq(mdAlreadyDeleted.getBusinessKeyHash()), anyString());
        // 业务表只对 u-removed 的 targetId 软删（u-old 已删，不重复）
        verify(abstractUserMapper).softDeleteBatch(eq(TENANT_ID), eq(List.of(200L)), any(LocalDateTime.class));
        verify(abstractUserMapper, never()).softDeleteBatch(eq(TENANT_ID), eq(List.of(300L)), any(LocalDateTime.class));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private AbstractUser buildUser(Long id) {
        return buildUser(id, "u-keep");
    }

    private AbstractUser buildUser(Long id, String externalId) {
        AbstractUser u = new AbstractUser();
        u.setId(id);
        u.setTenantId(TENANT_ID);
        u.setUserType(0);
        u.setExternalId(externalId);
        return u;
    }

    private SyncMetadata buildMetadata(Long targetId, String externalId,
                                       String status,
                                       LocalDateTime lastOccurredAt, Long lastSeq) {
        SyncMetadata md = new SyncMetadata();
        md.setTenantId(TENANT_ID);
        md.setEntityKind(ENTITY_KIND);
        md.setSourceService(SOURCE_SERVICE);
        String businessKey = SyncKeyCodec.abstractUserBusinessKey("USER", externalId);
        md.setBusinessKey(businessKey);
        // 与 service 内部 SyncKeyCodec.sha256Hex(businessKey) 一致，确保 seenBusinessKeyHashes 命中
        md.setBusinessKeyHash(SyncKeyCodec.sha256Hex(businessKey));
        md.setTargetId(targetId);
        md.setTargetStatus(status);
        md.setLastSyncOccurredAt(lastOccurredAt);
        md.setLastSyncSequenceNo(lastSeq);
        md.setDeleteFlag(0L);
        return md;
    }
}
