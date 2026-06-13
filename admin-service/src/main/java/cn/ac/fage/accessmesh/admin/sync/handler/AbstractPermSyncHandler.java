package cn.ac.fage.accessmesh.admin.sync.handler;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * 同步任务处理器抽象基类
 * <p>
 * 封装通用流程：
 * <ol>
 *   <li>反序列化 {@link SysSyncTask#getPayload()} 为 {@code Map<String,Object>}</li>
 *   <li>根据 {@code batchKey} 是否非空决定走 sync 还是 full-sync</li>
 *   <li>调用子类提供的 Feign 方法</li>
 *   <li>把 {@link PermResult}+{@link SyncResultResp} 映射为 {@link SyncTaskExecutionResult}</li>
 * </ol>
 * 子类只需绑定 syncAction 与 4 个 Feign 调用桩。
 * </p>
 */
public abstract class AbstractPermSyncHandler implements SyncTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractPermSyncHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final int HTTP_OK = 200;

    /** 默认支持的最大 payloadVersion；子类可覆写。 */
    protected static final int DEFAULT_SUPPORTED_MAX_VERSION = 1;

    private final ObjectMapper objectMapper;

    protected AbstractPermSyncHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 子类支持的最大 payloadVersion；默认 {@link #DEFAULT_SUPPORTED_MAX_VERSION}=1。
     * <p>当 task.payloadVersion 超过此值时直接返回 NON_RETRYABLE，且不调用 Feign。</p>
     */
    protected int supportedMaxVersion() {
        return DEFAULT_SUPPORTED_MAX_VERSION;
    }

    @Override
    public SyncTaskExecutionResult execute(SysSyncTask task) {
        // P5: payloadVersion 入口校验 — 高于支持范围直接 NON_RETRYABLE，不调 Feign
        Integer payloadVersion = task.getPayloadVersion();
        if (payloadVersion != null && payloadVersion > supportedMaxVersion()) {
            log.warn("sync handler {} rejects task {}: payloadVersion={} exceeds supportedMaxVersion={}",
                supportedAction(), task.getId(), payloadVersion, supportedMaxVersion());
            return SyncTaskExecutionResult.nonRetryable(
                "PAYLOAD_VERSION_UNSUPPORTED:" + payloadVersion);
        }
        Map<String, Object> payload = readPayload(task);
        try {
            PermResult<SyncResultResp> resp = (task.getBatchKey() != null && !task.getBatchKey().isBlank())
                ? callFullSync(payload)
                : callSync(payload);
            return mapResponse(resp);
        } catch (RuntimeException ex) {
            // Feign 抛出的 RetryableException / 网络层异常通常是 RuntimeException
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                log.warn("sync handler {} got IO exception, retryable: {}",
                    supportedAction(), cause.getMessage());
                return SyncTaskExecutionResult.retryable("IO_EXCEPTION:" + cause.getMessage());
            }
            // 其它运行时异常上抛由 Scheduler 兜底为 RETRYABLE
            throw ex;
        } catch (IOException ioe) {
            log.warn("sync handler {} got IO exception, retryable: {}",
                supportedAction(), ioe.getMessage());
            return SyncTaskExecutionResult.retryable("IO_EXCEPTION:" + ioe.getMessage());
        }
    }

    /** 增量同步 Feign 调用。 */
    protected abstract PermResult<SyncResultResp> callSync(Map<String, Object> payload)
        throws IOException;

    /** 全量同步 Feign 调用。 */
    protected abstract PermResult<SyncResultResp> callFullSync(Map<String, Object> payload)
        throws IOException;

    private Map<String, Object> readPayload(SysSyncTask task) {
        String json = task.getPayload();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "deserialize sync task payload failed: action=" + task.getSyncAction()
                    + ", taskId=" + task.getId(), e);
        }
    }

    private SyncTaskExecutionResult mapResponse(PermResult<SyncResultResp> resp) {
        if (resp == null) {
            return SyncTaskExecutionResult.retryable("EMPTY_RESPONSE");
        }
        SyncResultResp data = resp.getData();
        if (resp.getCode() != HTTP_OK) {
            // 非 200 表示业务/安全/技术拒绝；优先使用响应体中的 retryClass
            String retryClass = data != null ? data.retryClass() : null;
            String reason = data != null && data.reason() != null
                ? data.reason() : resp.getMessage();
            return classify(retryClass, reason);
        }
        if (data == null) {
            return SyncTaskExecutionResult.retryable("NULL_DATA");
        }
        if (data.stale()) {
            return SyncTaskExecutionResult.stale(data.reason());
        }
        if (data.applied()) {
            return SyncTaskExecutionResult.success();
        }
        // accepted=true 但 applied=false 且非 stale：依赖 retryClass 决定
        return classify(data.retryClass(), data.reason());
    }

    private SyncTaskExecutionResult classify(String retryClass, String reason) {
        if (retryClass == null) {
            return SyncTaskExecutionResult.retryable(reason != null ? reason : "UNKNOWN");
        }
        return switch (retryClass) {
            case SyncTaskExecutionResult.RETRYABLE -> SyncTaskExecutionResult.retryable(reason);
            case SyncTaskExecutionResult.DEPENDENCY_MISSING -> SyncTaskExecutionResult.dependencyMissing(reason);
            case SyncTaskExecutionResult.NON_RETRYABLE -> SyncTaskExecutionResult.nonRetryable(reason);
            case SyncTaskExecutionResult.SECURITY_DENIED -> SyncTaskExecutionResult.securityDenied(reason);
            case SyncTaskExecutionResult.STALE_VERSION -> SyncTaskExecutionResult.stale(reason);
            default -> SyncTaskExecutionResult.retryable("UNKNOWN_RETRY_CLASS:" + retryClass);
        };
    }
}
