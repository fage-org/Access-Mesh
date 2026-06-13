package cn.ac.fage.accessmesh.admin.sync.handler;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.SyncTaskFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P5 防回归：验证 {@link AbstractPermSyncHandler} 在 {@code task.payloadVersion} 高于
 * {@code supportedMaxVersion()} 时直接返回 {@code NON_RETRYABLE} 且不调用 Feign。
 */
@ExtendWith(MockitoExtension.class)
class PayloadVersionGuardTest {

    @Mock
    private SyncTaskFeignClient feignClient;

    private PermAbstractUserSyncHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PermAbstractUserSyncHandler(feignClient, new ObjectMapper());
    }

    private SysSyncTask task(Integer payloadVersion) {
        SysSyncTask t = new SysSyncTask();
        t.setId(1L);
        t.setSyncAction("PERM_ABSTRACT_USER_SYNC");
        t.setPayload("{\"operation\":\"UPSERT\",\"subjectExternalId\":\"10001\"}");
        t.setBatchKey(null);
        t.setPayloadVersion(payloadVersion);
        return t;
    }

    @Test
    @DisplayName("payloadVersion=1（合法）走 Feign 调用，返回 SUCCESS")
    void supportedVersion_invokesFeignOnce() {
        when(feignClient.syncAbstractUser(any()))
                .thenReturn(PermResult.success(new SyncResultResp(true, true, false, null, null)));

        SyncTaskExecutionResult result = handler.execute(task(1));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.SUCCESS);
        verify(feignClient, times(1)).syncAbstractUser(any());
        verify(feignClient, never()).fullSyncAbstractUser(any());
    }

    @Test
    @DisplayName("payloadVersion=999（超范围）零 Feign 调用，outcome=NON_RETRYABLE")
    void unsupportedVersion_skipsFeignAndReturnsNonRetryable() {
        SyncTaskExecutionResult result = handler.execute(task(999));

        assertThat(result.outcome()).isEqualTo(SyncTaskExecutionResult.NON_RETRYABLE);
        assertThat(result.reason()).contains("PAYLOAD_VERSION_UNSUPPORTED");
        assertThat(result.reason()).contains("999");
        verify(feignClient, never()).syncAbstractUser(any());
        verify(feignClient, never()).fullSyncAbstractUser(any());
    }
}
