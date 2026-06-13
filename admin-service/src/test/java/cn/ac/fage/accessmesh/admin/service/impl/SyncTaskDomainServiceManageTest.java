package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskBatchStatusReport;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskQueryParams;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SyncTaskDomainServiceImpl 管理面方法单元测试。
 * <p>
 * 验证 retryNow / resetTask / queryBatchStatus / listEnhanced 的领域规则与状态前置约束：
 * <ul>
 *   <li>SUCCESS 拒绝 retryNow（TASK_ALREADY_SUCCESS）</li>
 *   <li>PROCESSING 拒绝 resetTask（TASK_PROCESSING_NOT_RESETTABLE）</li>
 *   <li>queryBatchStatus 正确聚合 status / phase 计数与 currentPhase</li>
 *   <li>listEnhanced 正确组合 mapper.selectByQuery + countByQuery</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskDomainServiceManageTest {

    private static final Long TENANT_ID = 1L;

    @Mock
    private SysSyncTaskMapper syncTaskMapper;
    @Mock
    private AdminPermissionValidator permissionValidator;

    private SyncTaskDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncTaskDomainServiceImpl(syncTaskMapper, permissionValidator, new ObjectMapper());
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void retryNow_pendingTask_callsMapperMarkRetryNow() {
        SysSyncTask t = new SysSyncTask();
        t.setId(10L);
        t.setStatus("PENDING");
        when(syncTaskMapper.selectByIdSafe(eq(10L), eq(TENANT_ID))).thenReturn(t);
        when(syncTaskMapper.markRetryNow(eq(10L), eq(TENANT_ID), any(LocalDateTime.class))).thenReturn(1);

        service.retryNow(10L);

        verify(syncTaskMapper).markRetryNow(eq(10L), eq(TENANT_ID), any(LocalDateTime.class));
    }

    @Test
    void retryNow_failedTask_callsMapperMarkRetryNow() {
        SysSyncTask t = new SysSyncTask();
        t.setId(11L);
        t.setStatus("FAILED");
        when(syncTaskMapper.selectByIdSafe(eq(11L), eq(TENANT_ID))).thenReturn(t);
        when(syncTaskMapper.markRetryNow(eq(11L), eq(TENANT_ID), any(LocalDateTime.class))).thenReturn(1);

        service.retryNow(11L);

        verify(syncTaskMapper).markRetryNow(eq(11L), eq(TENANT_ID), any(LocalDateTime.class));
    }

    @Test
    void retryNow_successTask_throwsBizExceptionAlreadySuccess() {
        SysSyncTask t = new SysSyncTask();
        t.setId(12L);
        t.setStatus("SUCCESS");
        when(syncTaskMapper.selectByIdSafe(eq(12L), eq(TENANT_ID))).thenReturn(t);

        assertThatThrownBy(() -> service.retryNow(12L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("TASK_ALREADY_SUCCESS");

        verify(syncTaskMapper, never()).markRetryNow(any(), any(), any());
    }

    @Test
    void retryNow_taskNotFound_throwsBizException() {
        when(syncTaskMapper.selectByIdSafe(eq(99L), eq(TENANT_ID))).thenReturn(null);

        assertThatThrownBy(() -> service.retryNow(99L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("TASK_NOT_FOUND");
    }

    @Test
    void resetTask_processingTask_throwsBizException() {
        SysSyncTask t = new SysSyncTask();
        t.setId(20L);
        t.setStatus("PROCESSING");
        when(syncTaskMapper.selectByIdSafe(eq(20L), eq(TENANT_ID))).thenReturn(t);

        assertThatThrownBy(() -> service.resetTask(20L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("TASK_PROCESSING_NOT_RESETTABLE");

        verify(syncTaskMapper, never()).markReset(any(), any(), any());
    }

    @Test
    void resetTask_failedTask_callsMapperMarkReset() {
        SysSyncTask t = new SysSyncTask();
        t.setId(21L);
        t.setStatus("FAILED");
        when(syncTaskMapper.selectByIdSafe(eq(21L), eq(TENANT_ID))).thenReturn(t);
        when(syncTaskMapper.markReset(eq(21L), eq(TENANT_ID), any(LocalDateTime.class))).thenReturn(1);

        service.resetTask(21L);

        verify(syncTaskMapper).markReset(eq(21L), eq(TENANT_ID), any(LocalDateTime.class));
    }

    @Test
    void queryBatchStatus_aggregatesStatusAndPhase() {
        // mixed: 2 PENDING (USER_RESOURCE), 1 PROCESSING (USER_SUBJECT), 3 SUCCESS (USER_SUBJECT), 1 FAILED (USER_RESOURCE)
        when(syncTaskMapper.selectAllByBatchKeyHash(eq(TENANT_ID), anyString())).thenReturn(List.of(
            taskOf(1L, "PENDING", "USER_RESOURCE"),
            taskOf(2L, "PENDING", "USER_RESOURCE"),
            taskOf(3L, "PROCESSING", "USER_SUBJECT"),
            taskOf(4L, "SUCCESS", "USER_SUBJECT"),
            taskOf(5L, "SUCCESS", "USER_SUBJECT"),
            taskOf(6L, "SUCCESS", "USER_SUBJECT"),
            taskOf(7L, "FAILED", "USER_RESOURCE")
        ));

        SyncTaskBatchStatusReport report = service.queryBatchStatus("sourceService=admin-service&runId=u-1");

        assertThat(report.totalTasks()).isEqualTo(7L);
        assertThat(report.statusCounts()).containsEntry("PENDING", 2L);
        assertThat(report.statusCounts()).containsEntry("PROCESSING", 1L);
        assertThat(report.statusCounts()).containsEntry("SUCCESS", 3L);
        assertThat(report.statusCounts()).containsEntry("FAILED", 1L);
        assertThat(report.hasFailed()).isTrue();
        // PROCESSING USER_SUBJECT phaseOrder=1 < PENDING USER_RESOURCE phaseOrder=2
        assertThat(report.currentPhase()).isEqualTo("USER_SUBJECT");
        assertThat(report.phaseCounts()).containsKey("USER_SUBJECT");
        assertThat(report.phaseCounts().get("USER_SUBJECT")).containsEntry("SUCCESS", 3L);
    }

    @Test
    void queryBatchStatus_allDone_currentPhaseNull() {
        when(syncTaskMapper.selectAllByBatchKeyHash(eq(TENANT_ID), anyString())).thenReturn(List.of(
            taskOf(1L, "SUCCESS", "USER_SUBJECT"),
            taskOf(2L, "SUCCESS", "USER_RESOURCE")
        ));
        SyncTaskBatchStatusReport report = service.queryBatchStatus("sourceService=admin-service&runId=u-2");

        assertThat(report.currentPhase()).isNull();
        assertThat(report.hasFailed()).isFalse();
        assertThat(report.statusCounts()).containsEntry("SUCCESS", 2L);
    }

    @Test
    void listEnhanced_combinesSelectAndCount() {
        SyncTaskQueryParams params = new SyncTaskQueryParams(
            null, "PERM_ABSTRACT_USER_SYNC", "PENDING", "USER_SUBJECT", null, null);
        SysSyncTask t = new SysSyncTask();
        t.setId(50L);
        when(syncTaskMapper.countByQuery(any(SyncTaskQueryParams.class))).thenReturn(1L);
        when(syncTaskMapper.selectByQuery(any(SyncTaskQueryParams.class), eq(0L), eq(20L)))
            .thenReturn(List.of(t));

        PaginatedResult<SysSyncTask> result = service.listEnhanced(params, 1, 20);

        ArgumentCaptor<SyncTaskQueryParams> cap = ArgumentCaptor.forClass(SyncTaskQueryParams.class);
        verify(syncTaskMapper).selectByQuery(cap.capture(), eq(0L), eq(20L));
        assertThat(cap.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(cap.getValue().syncAction()).isEqualTo("PERM_ABSTRACT_USER_SYNC");
        assertThat(result.items()).hasSize(1);
        assertThat(result.pagination().total()).isEqualTo(1L);
        assertThat(result.pagination().page()).isEqualTo(1);
        assertThat(result.pagination().size()).isEqualTo(20);
    }

    @Test
    void listEnhanced_pagination_offsetCalculation() {
        SyncTaskQueryParams params = new SyncTaskQueryParams(null, null, null, null, null, null);
        when(syncTaskMapper.countByQuery(any())).thenReturn(123L);
        when(syncTaskMapper.selectByQuery(any(), anyLong(), anyLong())).thenReturn(List.of());

        // page 3, size 10 -> offset = 20
        service.listEnhanced(params, 3, 10);

        verify(syncTaskMapper).selectByQuery(any(), eq(20L), eq(10L));
    }

    @Test
    void getByIdSafe_returnsTaskFromMapper() {
        SysSyncTask t = new SysSyncTask();
        t.setId(99L);
        when(syncTaskMapper.selectByIdSafe(eq(99L), eq(TENANT_ID))).thenReturn(t);

        SysSyncTask result = service.getByIdSafe(99L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);
    }

    private SysSyncTask taskOf(Long id, String status, String phase) {
        SysSyncTask t = new SysSyncTask();
        t.setId(id);
        t.setStatus(status);
        t.setPhase(phase);
        return t;
    }
}
