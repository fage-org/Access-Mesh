package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.access.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskClaimTimeouts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link SyncTaskDomainServiceImpl} 的 claim/mark*ByWorker 调用对 Mapper 的传参正确，
 * 并校验 CAS 风格（locked_by 必须传入）。
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskDomainServiceClaimTest {

    private static final String WORKER = "worker-abc";

    @Mock
    private SysSyncTaskMapper mapper;

    @Mock
    private AdminPermissionValidator permissionValidator;

    private SyncTaskDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncTaskDomainServiceImpl(mapper, permissionValidator, new ObjectMapper());
    }

    @Test
    void claimDueTasks_passesArgsThrough() {
        when(mapper.claimDueTasks(any(), any(), any(), any(), any(), any(), any(), eq(WORKER), eq(50)))
            .thenReturn(List.of(new SysSyncTask()));

        SyncTaskClaimTimeouts timeouts = new SyncTaskClaimTimeouts(
            Duration.ofSeconds(60),
            Duration.ofSeconds(300),
            Map.of(SyncTaskBuilder.ACTION_USER_ROLE_SYNC, Duration.ofSeconds(120))
        );
        List<SysSyncTask> tasks = service.claimDueTasks(50, WORKER, timeouts);

        assertThat(tasks).hasSize(1);
        ArgumentCaptor<LocalDateTime> nowCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> defaultCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> fullSyncCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> abstractUserCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> abstractRoleCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> userRoleCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> resourceEntityCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).claimDueTasks(
            nowCap.capture(),
            defaultCap.capture(),
            fullSyncCap.capture(),
            abstractUserCap.capture(),
            abstractRoleCap.capture(),
            userRoleCap.capture(),
            resourceEntityCap.capture(),
            eq(WORKER),
            eq(50)
        );
        assertThat(Duration.between(defaultCap.getValue(), nowCap.getValue()).getSeconds()).isEqualTo(60);
        assertThat(Duration.between(fullSyncCap.getValue(), nowCap.getValue()).getSeconds()).isEqualTo(300);
        assertThat(Duration.between(abstractUserCap.getValue(), nowCap.getValue()).getSeconds()).isEqualTo(60);
        assertThat(Duration.between(abstractRoleCap.getValue(), nowCap.getValue()).getSeconds()).isEqualTo(60);
        assertThat(Duration.between(userRoleCap.getValue(), nowCap.getValue()).getSeconds()).isEqualTo(120);
        assertThat(Duration.between(resourceEntityCap.getValue(), nowCap.getValue()).getSeconds()).isEqualTo(60);

        LocalDateTime processingLocked120SecondsAgo = nowCap.getValue().minusSeconds(120);
        assertThat(processingLocked120SecondsAgo).isBefore(defaultCap.getValue());
        assertThat(processingLocked120SecondsAgo).isAfter(fullSyncCap.getValue());
    }

    @Test
    void claimDueTasks_zeroBatch_returnsEmpty() {
        assertThat(service.claimDueTasks(0, WORKER, new SyncTaskClaimTimeouts(
            Duration.ofSeconds(60), Duration.ofSeconds(300), Map.of()
        ))).isEmpty();
        verify(mapper, never()).claimDueTasks(any(), any(), any(), any(), any(), any(), any(), anyString(), anyInt());
    }

    @Test
    void claimDueTasks_blankWorker_throws() {
        assertThatThrownBy(() -> service.claimDueTasks(10, " ", new SyncTaskClaimTimeouts(
            Duration.ofSeconds(60), Duration.ofSeconds(300), Map.of()
        )))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimDueTasks_sqlUsesFullSyncTimeoutForProcessingBatchTasks() throws Exception {
        try (var in = Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream("mapper/SysSyncTaskMapper.xml")
        )) {
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(xml)
                .contains("s.status = 'PROCESSING'")
                .contains("s.batch_key_hash IS NOT NULL THEN #{fullSyncStaleBefore}")
                .contains("PERM_ABSTRACT_USER_SYNC")
                .contains("PERM_ABSTRACT_ROLE_SYNC")
                .contains("PERM_USER_ROLE_SYNC")
                .contains("PERM_RESOURCE_ENTITY_SYNC")
                .contains("#{defaultStaleBefore}");
        }
    }

    @Test
    void markSuccess_byWorker_passesCasArgs() {
        when(mapper.markSuccessByWorker(eq(99L), eq(WORKER), any())).thenReturn(1);

        service.markSuccess(99L, WORKER);

        verify(mapper).markSuccessByWorker(eq(99L), eq(WORKER), any(LocalDateTime.class));
    }

    @Test
    void markRetryable_byWorker_passesArgsAndComputesNextRetry() {
        when(mapper.markRetryableByWorker(eq(7L), eq(WORKER), anyString(), any(), any())).thenReturn(1);

        service.markRetryable(7L, WORKER, "io fail", Duration.ofSeconds(20));

        ArgumentCaptor<LocalDateTime> nextCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).markRetryableByWorker(eq(7L), eq(WORKER),
            eq("io fail"), nextCap.capture(), nowCap.capture());
        assertThat(Duration.between(nowCap.getValue(), nextCap.getValue()).getSeconds()).isEqualTo(20);
    }

    @Test
    void markFailed_byWorker_composesRetryClassAndReason() {
        when(mapper.markFailedByWorker(eq(3L), eq(WORKER), anyString(), any())).thenReturn(1);

        service.markFailed(3L, WORKER, "NON_RETRYABLE", "BAD_PAYLOAD");

        ArgumentCaptor<String> errCap = ArgumentCaptor.forClass(String.class);
        verify(mapper).markFailedByWorker(eq(3L), eq(WORKER), errCap.capture(), any(LocalDateTime.class));
        assertThat(errCap.getValue()).startsWith("NON_RETRYABLE:").contains("BAD_PAYLOAD");
    }

    @Test
    void markSuccess_blankWorker_throws() {
        assertThatThrownBy(() -> service.markSuccess(1L, ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markSuccess_nullId_throws() {
        assertThatThrownBy(() -> service.markSuccess(null, WORKER))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
