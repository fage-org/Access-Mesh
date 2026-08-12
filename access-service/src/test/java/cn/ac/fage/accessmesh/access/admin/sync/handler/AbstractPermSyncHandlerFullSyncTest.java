package cn.ac.fage.accessmesh.access.admin.sync.handler;

import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link AbstractPermSyncHandler} 在 full-sync 路径下能正确处理新的
 * {@link SyncResultResp} 顶层字段（含 {@code detail}）。
 * <p>
 * 防回归：sync 路径 {@code detail = null} 时不应破坏现有语义；
 * full-sync 路径 {@code detail != null} 时按顶层 retryClass 决定 outcome。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AbstractPermSyncHandlerFullSyncTest {

    @Mock
    private SyncTaskFeignClient feignClient;

    private PermAbstractUserSyncHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PermAbstractUserSyncHandler(feignClient, new ObjectMapper());
    }

    private SysSyncTask fullSyncTask() {
        SysSyncTask t = new SysSyncTask();
        t.setId(1L);
        t.setSyncAction("PERM_ABSTRACT_USER_SYNC");
        t.setPayload("{\"scope\":{\"sourceService\":\"x\",\"subjectTypeCode\":\"USER\"},\"items\":[]}");
        t.setBatchKey("batch-001");
        return t;
    }

    private SysSyncTask syncTask() {
        SysSyncTask t = new SysSyncTask();
        t.setId(2L);
        t.setSyncAction("PERM_ABSTRACT_USER_SYNC");
        t.setPayload("{\"operation\":\"UPSERT\"}");
        t.setBatchKey(null);
        return t;
    }

    /** Scenario 1: detail 非 null + applied=true → SUCCESS */
    @Test
    void fullSync_detailPresent_appliedTrue_mapsToSuccess() {
        SyncResultResp.FullSyncDetail detail = new SyncResultResp.FullSyncDetail(
                3, 0, 0, 0,
                List.of(new SyncResultResp.ItemResult("k1", true, false, null, null)));
        SyncResultResp resp = new SyncResultResp(true, true, false, null, null, detail);
        when(feignClient.fullSyncAbstractUser(any())).thenReturn(PermResult.success(resp));

        SyncTaskExecutionResult result = handler.execute(fullSyncTask());

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.SUCCESS);
    }

    /** Scenario 2: detail 非 null + applied=false + retryClass=RETRYABLE → RETRYABLE */
    @Test
    void fullSync_detailPresent_partialFailure_mapsToRetryable() {
        SyncResultResp.FullSyncDetail detail = new SyncResultResp.FullSyncDetail(
                2, 0, 1, 0,
                List.of(new SyncResultResp.ItemResult("k1", true, false, null, null),
                        new SyncResultResp.ItemResult("k2", false, false,
                                "DEPENDENCY_MISSING", "PARENT_NOT_FOUND")));
        SyncResultResp resp = new SyncResultResp(true, false, false,
                "RETRYABLE", "FULL_SYNC_PARTIAL_FAILURE", detail);
        when(feignClient.fullSyncAbstractUser(any())).thenReturn(PermResult.success(resp));

        SyncTaskExecutionResult result = handler.execute(fullSyncTask());

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.RETRYABLE);
        assertThat(result.reason()).isEqualTo("FULL_SYNC_PARTIAL_FAILURE");
    }

    /** Scenario 3: detail 非 null 但顶层 retryClass=DEPENDENCY_MISSING → DEPENDENCY_MISSING */
    @Test
    void fullSync_detailPresent_topLevelDependencyMissing_mapsToDependencyMissing() {
        SyncResultResp.FullSyncDetail detail = new SyncResultResp.FullSyncDetail(
                0, 0, 1, 0,
                List.of(new SyncResultResp.ItemResult("k1", false, false,
                        "DEPENDENCY_MISSING", "PARENT_ROLE_NOT_FOUND")));
        SyncResultResp resp = new SyncResultResp(false, false, false,
                "DEPENDENCY_MISSING", "PARENT_ROLE_NOT_FOUND", detail);
        when(feignClient.fullSyncAbstractUser(any()))
                .thenReturn(new PermResult<>(409, "dependency missing", resp, null, null));

        SyncTaskExecutionResult result = handler.execute(fullSyncTask());

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.DEPENDENCY_MISSING);
        assertThat(result.reason()).isEqualTo("PARENT_ROLE_NOT_FOUND");
    }

    /** Scenario 4: detail = null（sync 路径）+ applied=true → SUCCESS（兼容 sync 不破坏） */
    @Test
    void sync_detailNull_appliedTrue_mapsToSuccess() {
        SyncResultResp resp = new SyncResultResp(true, true, false, null, null);
        assertThat(resp.detail()).isNull(); // 5 字段构造 detail 默认 null
        when(feignClient.syncAbstractUser(any())).thenReturn(PermResult.success(resp));

        SyncTaskExecutionResult result = handler.execute(syncTask());

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.SUCCESS);
    }
}
