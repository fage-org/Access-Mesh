package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskBatchStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskQueryReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskRebuildFromFactReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskBatchStatusResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskDetailResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskRebuildResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskBatchStatusReport;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskQueryParams;
import cn.ac.fage.accessmesh.access.admin.sync.orchestrator.SyncFullSyncOrchestrator;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SyncTaskController 单元测试。
 * <p>
 * 验证管理面补偿与观测接口的参数透传与编排：retry-now / reset / rebuild-from-fact /
 * list / batch-status。直接以 controller 实例 + mock 协作者方式验证调用关系，
 * 避免 SpringBootTest 完整上下文启动。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskAdminControllerTest {

    private static final Long TENANT_ID = 1L;

    @Mock
    private SyncTaskDomainService domainService;
    @Mock
    private AdminPermissionValidator permissionValidator;
    @Mock
    private SyncFullSyncOrchestrator orchestrator;

    private SyncTaskController controller;

    @BeforeEach
    void setUp() {
        controller = new SyncTaskController(domainService, permissionValidator, orchestrator);
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void retryNow_callsDomainService() {
        IdReq req = new IdReq(123L);

        PermResult<Void> result = controller.retryNow(req);

        verify(domainService).retryNow(eq(123L));
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    void reset_callsDomainService() {
        IdReq req = new IdReq(456L);

        PermResult<Void> result = controller.reset(req);

        verify(domainService).resetTask(eq(456L));
        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    void rebuildFromFact_callsOrchestratorAndReturnsBatchKey() {
        SyncTaskRebuildFromFactReq req = new SyncTaskRebuildFromFactReq("admin-service", "manual:alice");
        when(orchestrator.startFullSyncRun(eq(TENANT_ID), eq("admin-service"), eq("manual:alice")))
            .thenReturn("sourceService=admin-service&runId=u-1");
        when(domainService.queryBatchStatus(eq("sourceService=admin-service&runId=u-1")))
            .thenReturn(new SyncTaskBatchStatusReport(
                "sourceService=admin-service&runId=u-1", 9L,
                Map.of(), Map.of(), "USER_SUBJECT", false
            ));

        PermResult<SyncTaskRebuildResp> result = controller.rebuildFromFact(req);

        verify(orchestrator).startFullSyncRun(eq(TENANT_ID), eq("admin-service"), eq("manual:alice"));
        SyncTaskRebuildResp body = result.getData();
        assertThat(body).isNotNull();
        assertThat(body.batchKey()).isEqualTo("sourceService=admin-service&runId=u-1");
        assertThat(body.taskCount()).isEqualTo(9L);
    }

    @Test
    void list_passesParametersThrough() {
        SyncTaskQueryReq req = new SyncTaskQueryReq(
            "PERM_ABSTRACT_USER_SYNC", "PENDING", "USER_SUBJECT",
            null, null, 2, 50);
        SysSyncTask task = new SysSyncTask();
        task.setId(7L);
        task.setStatus("PENDING");
        when(domainService.listEnhanced(any(SyncTaskQueryParams.class), eq(2), eq(50)))
            .thenReturn(new PaginatedResult<>(List.of(task),
                new PaginatedResult.PaginationMeta(1, 2, 50, 1)));

        PermResult<PaginatedResult<cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskResp>> result =
            controller.list(req);

        ArgumentCaptor<SyncTaskQueryParams> cap = ArgumentCaptor.forClass(SyncTaskQueryParams.class);
        verify(domainService).listEnhanced(cap.capture(), eq(2), eq(50));
        assertThat(cap.getValue().syncAction()).isEqualTo("PERM_ABSTRACT_USER_SYNC");
        assertThat(cap.getValue().status()).isEqualTo("PENDING");
        assertThat(cap.getValue().phase()).isEqualTo("USER_SUBJECT");
        assertThat(result.getData().items()).hasSize(1);
    }

    @Test
    void batchStatus_returnsReportFields() {
        SyncTaskBatchStatusReq req = new SyncTaskBatchStatusReq("sourceService=admin-service&runId=x");
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("PENDING", 2L);
        statusCounts.put("SUCCESS", 5L);
        Map<String, Map<String, Long>> phaseCounts = new LinkedHashMap<>();
        phaseCounts.put("USER_SUBJECT", Map.of("SUCCESS", 5L));
        phaseCounts.put("USER_RESOURCE", Map.of("PENDING", 2L));
        when(domainService.queryBatchStatus(eq("sourceService=admin-service&runId=x")))
            .thenReturn(new SyncTaskBatchStatusReport(
                "sourceService=admin-service&runId=x", 7L, statusCounts, phaseCounts,
                "USER_RESOURCE", false));

        PermResult<SyncTaskBatchStatusResp> result = controller.batchStatus(req);

        SyncTaskBatchStatusResp body = result.getData();
        assertThat(body.totalTasks()).isEqualTo(7L);
        assertThat(body.currentPhase()).isEqualTo("USER_RESOURCE");
        assertThat(body.hasFailed()).isFalse();
        assertThat(body.statusCounts()).containsEntry("SUCCESS", 5L);
    }

    @Test
    void detail_returnsTaskWhenFound() {
        SysSyncTask task = new SysSyncTask();
        task.setId(99L);
        task.setStatus("FAILED");
        task.setPayload("{\"k\":\"v\"}");
        when(domainService.getByIdSafe(eq(99L))).thenReturn(task);

        PermResult<SyncTaskDetailResp> result = controller.detail(new IdReq(99L));

        SyncTaskDetailResp body = result.getData();
        assertThat(body.id()).isEqualTo(99L);
        assertThat(body.payload()).isEqualTo("{\"k\":\"v\"}");
        verify(domainService, never()).retryNow(any());
    }

    @Test
    void detail_throwsBizExceptionWhenNotFound() {
        when(domainService.getByIdSafe(org.mockito.ArgumentMatchers.anyLong())).thenReturn(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.detail(new IdReq(999L)))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .hasMessageContaining("TASK_NOT_FOUND");
    }
}
