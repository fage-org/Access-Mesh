package cn.ac.fage.accessmesh.admin.sync.handler;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link PermAbstractUserSyncHandler} 把 Feign 响应映射为正确的 outcome。
 */
@ExtendWith(MockitoExtension.class)
class PermAbstractUserSyncHandlerTest {

    @Mock
    private SyncTaskFeignClient feignClient;

    private PermAbstractUserSyncHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PermAbstractUserSyncHandler(feignClient, new ObjectMapper());
    }

    private SysSyncTask sampleTask(String batchKey) {
        SysSyncTask t = new SysSyncTask();
        t.setId(1L);
        t.setSyncAction("PERM_ABSTRACT_USER_SYNC");
        t.setPayload("{\"operation\":\"UPSERT\",\"subjectExternalId\":\"10001\"}");
        t.setBatchKey(batchKey);
        return t;
    }

    @Test
    void appliedTrue_mapsToSuccess() {
        when(feignClient.syncAbstractUser(any()))
            .thenReturn(PermResult.success(new SyncResultResp(true, true, false, null, null)));

        SyncTaskExecutionResult result = handler.execute(sampleTask(null));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.SUCCESS);
        verify(feignClient, never()).fullSyncAbstractUser(any());
    }

    @Test
    void staleTrue_mapsToStaleVersion() {
        when(feignClient.syncAbstractUser(any()))
            .thenReturn(PermResult.success(new SyncResultResp(true, false, true,
                "STALE_VERSION", "SYNC_VERSION_STALE")));

        SyncTaskExecutionResult result = handler.execute(sampleTask(null));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.STALE_VERSION);
    }

    @Test
    void nonOkCodeWithDependencyMissing_mapsToDependencyMissing() {
        when(feignClient.syncAbstractUser(any()))
            .thenReturn(new PermResult<>(409, "dependency missing",
                new SyncResultResp(false, false, false, "DEPENDENCY_MISSING", "PARENT_NOT_FOUND"),
                null, null));

        SyncTaskExecutionResult result = handler.execute(sampleTask(null));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.DEPENDENCY_MISSING);
        assertThat(result.reason()).isEqualTo("PARENT_NOT_FOUND");
    }

    @Test
    void securityDenied_mapsCorrectly() {
        when(feignClient.syncAbstractUser(any()))
            .thenReturn(new PermResult<>(403, "denied",
                new SyncResultResp(false, false, false, "SECURITY_DENIED", "SIGN_FAIL"),
                null, null));

        SyncTaskExecutionResult result = handler.execute(sampleTask(null));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.SECURITY_DENIED);
    }

    @Test
    void nonRetryable_mapsCorrectly() {
        when(feignClient.syncAbstractUser(any()))
            .thenReturn(new PermResult<>(400, "bad",
                new SyncResultResp(false, false, false, "NON_RETRYABLE", "BAD_PAYLOAD"),
                null, null));

        SyncTaskExecutionResult result = handler.execute(sampleTask(null));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.NON_RETRYABLE);
    }

    @Test
    void ioExceptionWrappedAsRuntime_mapsToRetryable() {
        when(feignClient.syncAbstractUser(any()))
            .thenThrow(new RuntimeException("io fail", new IOException("boom")));

        SyncTaskExecutionResult result = handler.execute(sampleTask(null));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.RETRYABLE);
        assertThat(result.reason()).contains("boom");
    }

    @Test
    void batchKeyPresent_routesToFullSync() {
        when(feignClient.fullSyncAbstractUser(any()))
            .thenReturn(PermResult.success(new SyncResultResp(true, true, false, null, null)));

        SyncTaskExecutionResult result = handler.execute(sampleTask("batch-001"));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.SUCCESS);
        verify(feignClient, never()).syncAbstractUser(any());
    }

    @Test
    void supportedActionMatchesConstant() {
        assertThat(handler.supportedAction()).isEqualTo("PERM_ABSTRACT_USER_SYNC");
    }

    @Test
    void payloadIsDeserializedToMap() {
        when(feignClient.syncAbstractUser(any(Map.class)))
            .thenReturn(PermResult.success(new SyncResultResp(true, true, false, null, null)));

        handler.execute(sampleTask(null));
        verify(feignClient).syncAbstractUser(any(Map.class));
    }
}
