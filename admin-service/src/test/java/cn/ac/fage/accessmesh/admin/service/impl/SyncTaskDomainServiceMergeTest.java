package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SyncTaskDomainServiceImpl PENDING 合并语义测试。
 * <p>
 * 验证 §2.1 任务模型标准：相同 (tenantId, syncAction, businessKey_hash) 已存在 PENDING 时
 * 仅 update，不存在时 insert；messageKey null 时由实现填充 UUID。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskDomainServiceMergeTest {

    private static final Long TENANT_ID = 1L;
    private static final String SYNC_ACTION = "PERM_ABSTRACT_USER_SYNC";
    private static final String BUSINESS_KEY = "subjectTypeCode=ADMIN_USER&subjectExternalId=10001";

    @Mock
    private SysSyncTaskMapper syncTaskMapper;

    @Mock
    private AdminPermissionValidator permissionValidator;

    private SyncTaskDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncTaskDomainServiceImpl(syncTaskMapper, permissionValidator, new ObjectMapper());
    }

    @Test
    @DisplayName("已存在 PENDING：仅 update，不 insert")
    void enqueue_pendingExists_onlyUpdates() {
        SysSyncTask existing = new SysSyncTask();
        existing.setId(99L);
        existing.setStatus("PENDING");
        existing.setRetryCount(2);
        when(syncTaskMapper.selectPendingByBusinessKeyHash(eq(TENANT_ID), eq(SYNC_ACTION), anyString()))
            .thenReturn(existing);

        SyncTaskEnvelope env = sampleEnvelope("msg-001");
        service.enqueue(TENANT_ID, env);

        // update should be called once
        verify(syncTaskMapper, times(1)).update(any(SysSyncTask.class));
        // insert never called
        verify(syncTaskMapper, never()).insert(any(SysSyncTask.class));

        // retry_count not reset
        ArgumentCaptor<SysSyncTask> captor = ArgumentCaptor.forClass(SysSyncTask.class);
        verify(syncTaskMapper).update(captor.capture());
        assertThat(captor.getValue().getRetryCount()).isEqualTo(2);
        assertThat(captor.getValue().getMessageKey()).isEqualTo("msg-001");
        assertThat(captor.getValue().getPayload()).isEqualTo(env.payload());
    }

    @Test
    @DisplayName("不存在 PENDING：insert 一次")
    void enqueue_pendingMissing_insertsNewRow() {
        when(syncTaskMapper.selectPendingByBusinessKeyHash(eq(TENANT_ID), eq(SYNC_ACTION), anyString()))
            .thenReturn(null);

        SyncTaskEnvelope env = sampleEnvelope("msg-002");
        service.enqueue(TENANT_ID, env);

        verify(syncTaskMapper, times(1)).insert(any(SysSyncTask.class));
        verify(syncTaskMapper, never()).update(any(SysSyncTask.class));

        ArgumentCaptor<SysSyncTask> captor = ArgumentCaptor.forClass(SysSyncTask.class);
        verify(syncTaskMapper).insert(captor.capture());
        SysSyncTask saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getSyncAction()).isEqualTo(SYNC_ACTION);
        assertThat(saved.getBusinessKey()).isEqualTo(BUSINESS_KEY);
        assertThat(saved.getBusinessKeyHash()).hasSize(64);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getDeleteFlag()).isZero();
    }

    @Test
    @DisplayName("messageKey null 时由实现填充 UUID（length=36, 含 4 个 -）")
    void enqueue_nullMessageKey_generatesUuid() {
        when(syncTaskMapper.selectPendingByBusinessKeyHash(eq(TENANT_ID), eq(SYNC_ACTION), anyString()))
            .thenReturn(null);

        SyncTaskEnvelope env = sampleEnvelope(null);
        service.enqueue(TENANT_ID, env);

        ArgumentCaptor<SysSyncTask> captor = ArgumentCaptor.forClass(SysSyncTask.class);
        verify(syncTaskMapper).insert(captor.capture());
        String generated = captor.getValue().getMessageKey();
        assertThat(generated).isNotNull();
        assertThat(generated).hasSize(36);
        assertThat(generated.chars().filter(c -> c == '-').count()).isEqualTo(4);
    }

    @Test
    @DisplayName("displayAttrs 序列化为 JSON 字符串")
    void enqueue_displayAttrsSerialized() {
        when(syncTaskMapper.selectPendingByBusinessKeyHash(eq(TENANT_ID), eq(SYNC_ACTION), anyString()))
            .thenReturn(null);

        SyncTaskEnvelope env = sampleEnvelope("msg-003");
        service.enqueue(TENANT_ID, env);

        ArgumentCaptor<SysSyncTask> captor = ArgumentCaptor.forClass(SysSyncTask.class);
        verify(syncTaskMapper).insert(captor.capture());
        String json = captor.getValue().getDisplayAttrs();
        assertThat(json).contains("\"entityType\":\"abstract_user\"");
        assertThat(json).contains("\"externalId\":\"10001\"");
    }

    private SyncTaskEnvelope sampleEnvelope(String messageKey) {
        Map<String, Object> displayAttrs = new LinkedHashMap<>();
        displayAttrs.put("entityType", "abstract_user");
        displayAttrs.put("externalId", "10001");
        displayAttrs.put("operationType", "upsert");
        return new SyncTaskEnvelope(
            SYNC_ACTION,
            BUSINESS_KEY,
            null,
            "{\"operation\":\"UPSERT\",\"subjectTypeCode\":\"ADMIN_USER\",\"subjectExternalId\":\"10001\"}",
            1,
            displayAttrs,
            LocalDateTime.now(),
            123456789L,
            null,
            messageKey
        );
    }
}
