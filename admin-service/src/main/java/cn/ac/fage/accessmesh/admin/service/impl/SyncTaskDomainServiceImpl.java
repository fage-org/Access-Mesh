package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.constant.SyncTaskStatus;
import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysSyncTaskMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskBatchStatusReport;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskClaimTimeouts;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskQueryParams;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 同步任务领域服务实现
 * <p>
 * S4 任务生产器实施版本：
 * <ul>
 *     <li>{@link #enqueue} / {@link #enqueueAll} 接受强类型 {@link SyncTaskEnvelope}，
 *         按 §2.1 任务模型标准（businessKey + SHA-256 hash + PENDING 合并）落表。</li>
 *     <li>状态枚举仅使用 {@link SyncTaskStatus} 中的 PENDING/PROCESSING/SUCCESS/FAILED。</li>
 *     <li>任务认领（locked_by/locked_at）、payloadVersion 校验、phase 推进
 *         由 S5/S6 调度器实施，本类不实现。</li>
 * </ul>
 * </p>
 */
@Service
public class SyncTaskDomainServiceImpl implements SyncTaskDomainService {

    private static final Logger log = LoggerFactory.getLogger(SyncTaskDomainServiceImpl.class);

    private static final String TARGET_SERVICE = "permission-center";
    private static final int DEFAULT_MAX_RETRIES = 16;

    private final SysSyncTaskMapper syncTaskMapper;
    private final AdminPermissionValidator permissionValidator;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param syncTaskMapper      同步任务数据访问 Mapper
     * @param permissionValidator 权限校验器，校验任务操作权限
     * @param objectMapper        JSON 序列化工具，用于将 displayAttrs 写入 JSONB 列
     */
    public SyncTaskDomainServiceImpl(SysSyncTaskMapper syncTaskMapper,
                                     AdminPermissionValidator permissionValidator,
                                     ObjectMapper objectMapper) {
        this.syncTaskMapper = syncTaskMapper;
        this.permissionValidator = permissionValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(Long tenantId, SyncTaskEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope must not be null");
        }
        String businessKeyHash = sha256Hex(envelope.businessKey());
        String batchKeyHash = envelope.batchKey() == null ? null : sha256Hex(envelope.batchKey());
        String messageKey = envelope.messageKey() != null ? envelope.messageKey() : UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String displayAttrsJson = serializeDisplayAttrs(envelope.displayAttrs());

        SysSyncTask existing = syncTaskMapper.selectPendingByBusinessKeyHash(
            tenantId, envelope.syncAction(), businessKeyHash);

        if (existing != null) {
            // PENDING 合并：覆盖最新事件字段，retry_count 不重置
            existing.setMessageKey(messageKey);
            existing.setBusinessKey(envelope.businessKey());
            existing.setBusinessKeyHash(businessKeyHash);
            existing.setBatchKey(envelope.batchKey());
            existing.setBatchKeyHash(batchKeyHash);
            existing.setPayload(envelope.payload());
            existing.setPayloadVersion(envelope.payloadVersion());
            existing.setDisplayAttrs(displayAttrsJson);
            existing.setSyncOccurredAt(envelope.syncOccurredAt());
            existing.setSyncSequenceNo(envelope.syncSequenceNo());
            existing.setPhase(envelope.phase());
            existing.setUpdatedAt(now);
            syncTaskMapper.update(existing);
            return;
        }

        SysSyncTask task = new SysSyncTask();
        task.setTenantId(tenantId);
        task.setMessageKey(messageKey);
        task.setSyncAction(envelope.syncAction());
        task.setBusinessKey(envelope.businessKey());
        task.setBusinessKeyHash(businessKeyHash);
        task.setBatchKey(envelope.batchKey());
        task.setBatchKeyHash(batchKeyHash);
        task.setTargetService(TARGET_SERVICE);
        task.setPayload(envelope.payload());
        task.setPayloadVersion(envelope.payloadVersion());
        task.setDisplayAttrs(displayAttrsJson);
        task.setSyncOccurredAt(envelope.syncOccurredAt());
        task.setSyncSequenceNo(envelope.syncSequenceNo());
        task.setPhase(envelope.phase());
        task.setRetryCount(0);
        task.setMaxRetries(DEFAULT_MAX_RETRIES);
        task.setNextRetryAt(null);
        task.setStatus(SyncTaskStatus.PENDING);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setDeleteFlag(0L);
        syncTaskMapper.insert(task);
    }

    @Override
    public void enqueueAll(Long tenantId, List<SyncTaskEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return;
        }
        for (SyncTaskEnvelope envelope : envelopes) {
            enqueue(tenantId, envelope);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long id) {
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_TASK, id.toString(), AdminOperationCode.UPDATE);
        SysSyncTask record = syncTaskMapper.selectByIdSafe(id, TenantContextHolder.getTenantId());
        if (record != null) {
            record.setStatus(SyncTaskStatus.SUCCESS);
            record.setUpdatedAt(LocalDateTime.now());
            syncTaskMapper.update(record);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long id, String error) {
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_TASK, id.toString(), AdminOperationCode.UPDATE);
        SysSyncTask record = syncTaskMapper.selectByIdSafe(id, TenantContextHolder.getTenantId());
        if (record != null) {
            LocalDateTime now = LocalDateTime.now();
            int retryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
            int maxRetries = record.getMaxRetries() == null ? 0 : record.getMaxRetries();
            record.setRetryCount(retryCount + 1);
            record.setLastError(truncate(error, 1024));
            // S5 调度器实施前，沿用简化的指数退避；正式退避策略由 S5 提供。
            record.setNextRetryAt(now.plusMinutes(Math.min((retryCount + 1) * 5L, 60L)));
            record.setStatus(record.getRetryCount() >= maxRetries ? SyncTaskStatus.FAILED : SyncTaskStatus.PENDING);
            record.setUpdatedAt(now);
            syncTaskMapper.update(record);
        }
    }

    @Override
    public List<SysSyncTask> getDueTasks() {
        return syncTaskMapper.selectDueTasks(TenantContextHolder.getTenantId(), LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SysSyncTask> claimDueTasks(int batchSize, String workerId, SyncTaskClaimTimeouts timeouts) {
        if (batchSize <= 0) {
            return List.of();
        }
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        SyncTaskClaimTimeouts safeTimeouts = timeouts == null
            ? new SyncTaskClaimTimeouts(Duration.ofSeconds(60), Duration.ofSeconds(60), Map.of())
            : timeouts;
        LocalDateTime now = LocalDateTime.now();
        return syncTaskMapper.claimDueTasks(
            now,
            now.minus(safeTimeouts.defaultTimeout()),
            now.minus(safeTimeouts.fullSyncTimeout()),
            now.minus(safeTimeouts.timeoutForAction(SyncTaskBuilder.ACTION_ABSTRACT_USER_SYNC)),
            now.minus(safeTimeouts.timeoutForAction(SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC)),
            now.minus(safeTimeouts.timeoutForAction(SyncTaskBuilder.ACTION_USER_ROLE_SYNC)),
            now.minus(safeTimeouts.timeoutForAction(SyncTaskBuilder.ACTION_RESOURCE_ENTITY_SYNC)),
            workerId,
            batchSize
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long taskId, String workerId) {
        if (taskId == null || workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("taskId and workerId required");
        }
        int updated = syncTaskMapper.markSuccessByWorker(taskId, workerId, LocalDateTime.now());
        if (updated == 0) {
            log.warn("markSuccess CAS missed: taskId={}, workerId={}", taskId, workerId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRetryable(Long taskId, String workerId, String lastError, Duration backoff) {
        if (taskId == null || workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("taskId and workerId required");
        }
        Duration safeBackoff = backoff == null ? Duration.ofSeconds(5) : backoff;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryAt = now.plus(safeBackoff);
        int updated = syncTaskMapper.markRetryableByWorker(
            taskId, workerId, truncate(lastError, 1024), nextRetryAt, now);
        if (updated == 0) {
            log.warn("markRetryable CAS missed: taskId={}, workerId={}", taskId, workerId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long taskId, String workerId, String retryClass, String reason) {
        if (taskId == null || workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("taskId and workerId required");
        }
        String composedError = (retryClass == null ? "FAILED" : retryClass)
            + ":" + (reason == null ? "" : reason);
        int updated = syncTaskMapper.markFailedByWorker(
            taskId, workerId, truncate(composedError, 1024), LocalDateTime.now());
        if (updated == 0) {
            log.warn("markFailed CAS missed: taskId={}, workerId={}", taskId, workerId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcessed(Long id) {
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_TASK, id.toString(), AdminOperationCode.DELETE);
        SysSyncTask record = syncTaskMapper.selectByIdSafe(id, TenantContextHolder.getTenantId());
        if (record != null) {
            record.setDeleteFlag(record.getId());
            record.setDeletedAt(LocalDateTime.now());
            syncTaskMapper.update(record);
        }
    }

    @Override
    public PaginatedResult<SysSyncTask> page(PageReq pageReq) {
        Page<SysSyncTask> page = syncTaskMapper.paginateByTenantId(
            Page.of(pageReq.getPageNum(), pageReq.getPageSize()),
            TenantContextHolder.getTenantId()
        );
        long totalPages = (page.getTotalRow() + pageReq.getPageSize() - 1) / pageReq.getPageSize();
        return new PaginatedResult<>(page.getRecords(),
            new PaginatedResult.PaginationMeta(page.getTotalRow(), pageReq.getPageNum(), pageReq.getPageSize(), (int) totalPages));
    }

    /**
     * 计算字符串的 SHA-256 lowercase hex（长度 64）。
     */
    private static String sha256Hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("sha256Hex input must not be null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "SHA-256 algorithm not available", e);
        }
    }

    private String serializeDisplayAttrs(java.util.Map<String, Object> displayAttrs) {
        if (displayAttrs == null || displayAttrs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(displayAttrs);
        } catch (JsonProcessingException e) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "serialize displayAttrs failed", e);
        }
    }

    /**
     * 截断字符串，限制最大长度。
     *
     * @param s      原字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryNow(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_TASK, taskId.toString(), AdminOperationCode.UPDATE);
        Long tenantId = TenantContextHolder.getTenantId();
        SysSyncTask record = syncTaskMapper.selectByIdSafe(taskId, tenantId);
        if (record == null) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "TASK_NOT_FOUND: id=" + taskId);
        }
        String status = record.getStatus();
        if (SyncTaskStatus.SUCCESS.equals(status)) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "TASK_ALREADY_SUCCESS");
        }
        int updated = syncTaskMapper.markRetryNow(taskId, tenantId, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "TASK_RETRY_NOT_APPLICABLE: status=" + status);
        }
        log.info("retryNow: taskId={}, tenantId={}, prevStatus={}", taskId, tenantId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_TASK, taskId.toString(), AdminOperationCode.UPDATE);
        Long tenantId = TenantContextHolder.getTenantId();
        SysSyncTask record = syncTaskMapper.selectByIdSafe(taskId, tenantId);
        if (record == null) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "TASK_NOT_FOUND: id=" + taskId);
        }
        if (SyncTaskStatus.PROCESSING.equals(record.getStatus())) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "TASK_PROCESSING_NOT_RESETTABLE");
        }
        int updated = syncTaskMapper.markReset(taskId, tenantId, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "TASK_RESET_FAILED: status=" + record.getStatus());
        }
        log.info("resetTask: taskId={}, tenantId={}, prevStatus={}", taskId, tenantId, record.getStatus());
    }

    @Override
    public SyncTaskBatchStatusReport queryBatchStatus(String batchKey) {
        if (batchKey == null || batchKey.isBlank()) {
            throw new IllegalArgumentException("batchKey must not be blank");
        }
        Long tenantId = TenantContextHolder.getTenantId();
        String hash = sha256Hex(batchKey);
        List<SysSyncTask> tasks = syncTaskMapper.selectAllByBatchKeyHash(tenantId, hash);
        long total = tasks.size();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put(SyncTaskStatus.PENDING, 0L);
        statusCounts.put(SyncTaskStatus.PROCESSING, 0L);
        statusCounts.put(SyncTaskStatus.SUCCESS, 0L);
        statusCounts.put(SyncTaskStatus.FAILED, 0L);
        Map<String, Map<String, Long>> phaseCounts = new LinkedHashMap<>();
        boolean hasFailed = false;
        for (SysSyncTask t : tasks) {
            String s = t.getStatus() == null ? "UNKNOWN" : t.getStatus();
            statusCounts.merge(s, 1L, Long::sum);
            String p = t.getPhase() == null ? "UNKNOWN" : t.getPhase();
            phaseCounts.computeIfAbsent(p, k -> new LinkedHashMap<>())
                .merge(s, 1L, Long::sum);
            if (SyncTaskStatus.FAILED.equals(s)) {
                hasFailed = true;
            }
        }
        // currentPhase: PENDING/PROCESSING 中 phase 排序最小者
        String currentPhase = tasks.stream()
            .filter(t -> SyncTaskStatus.PENDING.equals(t.getStatus())
                || SyncTaskStatus.PROCESSING.equals(t.getStatus()))
            .map(SysSyncTask::getPhase)
            .filter(java.util.Objects::nonNull)
            .min(Comparator.comparingInt(SyncTaskDomainServiceImpl::phaseOrder)
                .thenComparing(Comparator.naturalOrder()))
            .orElse(null);
        return new SyncTaskBatchStatusReport(batchKey, total, statusCounts, phaseCounts, currentPhase, hasFailed);
    }

    private static int phaseOrder(String phase) {
        if (phase == null) return 99;
        switch (phase) {
            case "USER_SUBJECT": return 1;
            case "USER_RESOURCE": return 2;
            case "ORG_RESOURCE": return 3;
            case "ORG_ROLE": return 4;
            case "USER_ROLE": return 5;
            case "MENU_RESOURCE": return 6;
            case "OTHER_RESOURCE": return 7;
            default: return 99;
        }
    }

    @Override
    public PaginatedResult<SysSyncTask> listEnhanced(SyncTaskQueryParams params, int pageNum, int pageSize) {
        Long tenantId = TenantContextHolder.getTenantId();
        SyncTaskQueryParams effective = new SyncTaskQueryParams(
            tenantId,
            params == null ? null : params.syncAction(),
            params == null ? null : params.status(),
            params == null ? null : params.phase(),
            params == null ? null : params.batchKey(),
            params == null ? null : params.businessKey()
        );
        int safePage = Math.max(1, pageNum);
        int safeSize = pageSize < 1 ? 10 : Math.min(pageSize, 200);
        long offset = (long) (safePage - 1) * safeSize;
        long total = syncTaskMapper.countByQuery(effective);
        List<SysSyncTask> items = syncTaskMapper.selectByQuery(effective, offset, safeSize);
        int totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(total, safePage, safeSize, totalPages));
    }

    @Override
    public SysSyncTask getByIdSafe(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return syncTaskMapper.selectByIdSafe(taskId, TenantContextHolder.getTenantId());
    }
}
