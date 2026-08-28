package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.access.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService.ApplyVersionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncMetadataDomainServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final String SCOPE_KEY = "subjectTypeCode=USER";
    private static final String SCOPE_KEY_HASH = "scope-hash";
    private static final String BUSINESS_KEY = "subjectTypeCode=USER&subjectExternalId=u1";
    private static final String BUSINESS_KEY_HASH = "biz-hash";
    private static final String SYNC_KEY = "example-service|ABSTRACT_USER|" + BUSINESS_KEY;
    private static final String SYNC_KEY_HASH = "sync-hash";

    @Mock
    private SyncMetadataMapper syncMetadataMapper;

    private SyncMetadataDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncMetadataDomainServiceImpl(syncMetadataMapper);
    }

    @Test
    void applyVersion_shouldReturnStale_whenMapperReturnsZero_meaningExistingNewerOrEqual() {
        when(syncMetadataMapper.upsertIfNewer(any())).thenReturn(0);

        LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 13, 12, 0, 0);
        ApplyVersionResult result = service.applyVersion(
            TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, SCOPE_KEY,
            BUSINESS_KEY_HASH, BUSINESS_KEY,
            SYNC_KEY, SYNC_KEY_HASH,
            occurredAt, 5L);

        assertThat(result).isEqualTo(ApplyVersionResult.STALE);
        verify(syncMetadataMapper, never()).selectByBusinessKey(any(), anyString(), anyString(), anyString(), anyString());
        verify(syncMetadataMapper, never()).upsert(any());
        verify(syncMetadataMapper, never()).updateTargetId(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void applyVersion_shouldReturnApplied_whenMapperReturnsOne() {
        when(syncMetadataMapper.upsertIfNewer(any())).thenReturn(1);

        LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 13, 12, 0, 0);
        ApplyVersionResult result = service.applyVersion(
            TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, SCOPE_KEY,
            BUSINESS_KEY_HASH, BUSINESS_KEY,
            SYNC_KEY, SYNC_KEY_HASH,
            occurredAt, 11L);

        assertThat(result).isEqualTo(ApplyVersionResult.APPLIED);
        ArgumentCaptor<SyncMetadata> captor = ArgumentCaptor.forClass(SyncMetadata.class);
        verify(syncMetadataMapper, times(1)).upsertIfNewer(captor.capture());
        SyncMetadata saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getEntityKind()).isEqualTo("ABSTRACT_USER");
        assertThat(saved.getLastSyncOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getLastSyncSequenceNo()).isEqualTo(11L);
        assertThat(saved.getTargetStatus()).isEqualTo("ACTIVE");
        verify(syncMetadataMapper, never()).selectByBusinessKey(any(), anyString(), anyString(), anyString(), anyString());
        verify(syncMetadataMapper, never()).upsert(any());
    }

    @Test
    void applyVersion_shouldThrowIllegalArgument_whenOccurredAtIsNull() {
        assertThatThrownBy(() -> service.applyVersion(
            TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, SCOPE_KEY,
            BUSINESS_KEY_HASH, BUSINESS_KEY,
            SYNC_KEY, SYNC_KEY_HASH,
            null, 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markStatus_shouldThrowBizException_whenStatusNotAllowedForUserRole() {
        assertThatThrownBy(() -> service.markStatus(
            TENANT_ID, "USER_ROLE", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, "DISABLED"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("targetStatus");

        verify(syncMetadataMapper, never())
            .updateTargetStatus(any(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void markStatus_shouldThrowBizException_whenStatusNotAllowedForAbstractUser() {
        assertThatThrownBy(() -> service.markStatus(
            TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, "UNBOUND"))
            .isInstanceOf(BizException.class);
    }

    @Test
    void markStatus_shouldThrowBizException_whenEntityKindUnknown() {
        assertThatThrownBy(() -> service.markStatus(
            TENANT_ID, "UNKNOWN_KIND", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, "ACTIVE"))
            .isInstanceOf(BizException.class);
    }

    @Test
    void markStatus_shouldDelegateToMapper_whenValid() {
        when(syncMetadataMapper.updateTargetStatus(
            eq(TENANT_ID), eq("USER_ROLE"), eq(SOURCE_SERVICE),
            eq(SCOPE_KEY_HASH), eq(BUSINESS_KEY_HASH), eq("UNBOUND")))
            .thenReturn(1);

        int affected = service.markStatus(
            TENANT_ID, "USER_ROLE", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, "UNBOUND");

        assertThat(affected).isEqualTo(1);
    }

    @Test
    void backfillTargetId_shouldNoop_whenTargetIdIsNull() {
        service.backfillTargetId(TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, null);

        verify(syncMetadataMapper, never()).selectByBusinessKey(any(), anyString(), anyString(), anyString(), anyString());
        verify(syncMetadataMapper, never()).upsert(any());
        verify(syncMetadataMapper, never()).updateTargetId(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void backfillTargetId_shouldUpdateTargetIdDirectly_evenWhenNoRowMatches() {
        when(syncMetadataMapper.updateTargetId(
            eq(TENANT_ID), eq("ABSTRACT_USER"), eq(SOURCE_SERVICE),
            eq(SCOPE_KEY_HASH), eq(BUSINESS_KEY_HASH), eq(999L)))
            .thenReturn(0);

        service.backfillTargetId(TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, 999L);

        verify(syncMetadataMapper).updateTargetId(
            eq(TENANT_ID), eq("ABSTRACT_USER"), eq(SOURCE_SERVICE),
            eq(SCOPE_KEY_HASH), eq(BUSINESS_KEY_HASH), eq(999L));
        verify(syncMetadataMapper, never()).selectByBusinessKey(any(), anyString(), anyString(), anyString(), anyString());
        verify(syncMetadataMapper, never()).upsert(any());
    }

    @Test
    void backfillTargetId_shouldUpdateTargetIdDirectly_whenRowMatches() {
        when(syncMetadataMapper.updateTargetId(
            eq(TENANT_ID), eq("ABSTRACT_USER"), eq(SOURCE_SERVICE),
            eq(SCOPE_KEY_HASH), eq(BUSINESS_KEY_HASH), eq(777L)))
            .thenReturn(1);

        service.backfillTargetId(TENANT_ID, "ABSTRACT_USER", SOURCE_SERVICE,
            SCOPE_KEY_HASH, BUSINESS_KEY_HASH, 777L);

        verify(syncMetadataMapper, times(1)).updateTargetId(
            eq(TENANT_ID), eq("ABSTRACT_USER"), eq(SOURCE_SERVICE),
            eq(SCOPE_KEY_HASH), eq(BUSINESS_KEY_HASH), eq(777L));
        verify(syncMetadataMapper, never()).selectByBusinessKey(any(), anyString(), anyString(), anyString(), anyString());
        verify(syncMetadataMapper, never()).upsert(any());
    }

    /** T-PERM-022 评审修复：只读版本预判须与 upsertIfNewer 冲突谓词一致
     * （occurredAt 更大，或相等且 sequenceNo 更大；无现存记录=新）。 */
    @Test
    void isNewerVersionMirrorsUpsertIfNewerPredicate() {
        LocalDateTime existingOccurred = LocalDateTime.of(2026, 1, 1, 0, 0);
        SyncMetadata existing = new SyncMetadata();
        existing.setLastSyncOccurredAt(existingOccurred);
        existing.setLastSyncSequenceNo(5L);

        // 无现存记录 = 新
        assertThat(service.isNewerVersion(null, existingOccurred.minusSeconds(1), 1L)).isTrue();
        // occurredAt 更大 = 新（sequenceNo 无关）
        assertThat(service.isNewerVersion(existing, existingOccurred.plusSeconds(1), 1L)).isTrue();
        // occurredAt 相等且 sequenceNo 更大 = 新
        assertThat(service.isNewerVersion(existing, existingOccurred, 6L)).isTrue();
        // occurredAt 相等且 sequenceNo 相等/更小 = 旧
        assertThat(service.isNewerVersion(existing, existingOccurred, 5L)).isFalse();
        assertThat(service.isNewerVersion(existing, existingOccurred, 4L)).isFalse();
        // occurredAt 更小 = 旧
        assertThat(service.isNewerVersion(existing, existingOccurred.minusSeconds(1), 999L)).isFalse();
        // 亚微秒尾差先归一到微秒（评审修复）：现存 .123457（库内微秒化）与
        // 原值 .123456600（舍入后相等）按相等处理——序号更大即新（原实现误判旧）
        LocalDateTime microExisting = existingOccurred.withNano(123_457_000);
        SyncMetadata microMeta = new SyncMetadata();
        microMeta.setLastSyncOccurredAt(microExisting);
        microMeta.setLastSyncSequenceNo(1L);
        assertThat(service.isNewerVersion(microMeta, existingOccurred.withNano(123_456_600), 2L)).isTrue();
        assertThat(service.isNewerVersion(microMeta, existingOccurred.withNano(123_456_600), 1L)).isFalse();
        assertThat(service.isNewerVersion(microMeta, existingOccurred.withNano(123_456_499), 999L)).isFalse();
    }
}
