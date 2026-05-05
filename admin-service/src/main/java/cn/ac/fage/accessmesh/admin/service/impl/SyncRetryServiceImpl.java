package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import cn.ac.fage.accessmesh.admin.entity.table.SysSyncRetryTableDef;
import cn.ac.fage.accessmesh.admin.mapper.SysSyncRetryMapper;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import cn.ac.fage.accessmesh.common.mybatis.TenantAwareScheduled;
import cn.ac.fage.accessmesh.common.mybatis.TenantSafeQuery;


@Service
public class SyncRetryServiceImpl implements SyncRetryService {

    private static final Logger log = LoggerFactory.getLogger(SyncRetryServiceImpl.class);
    private static final int DEFAULT_MAX_RETRIES = 5;

    private final SysSyncRetryMapper syncRetryMapper;
    private final RestTemplate restTemplate;
    private final AdminPermissionValidator permissionValidator;

    public SyncRetryServiceImpl(SysSyncRetryMapper syncRetryMapper, RestTemplate restTemplate, AdminPermissionValidator permissionValidator) {
        this.syncRetryMapper = syncRetryMapper;
        this.restTemplate = restTemplate;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    public void recordSyncFailure(String messageKey, String targetService, String entityType,
                                   String externalId, String operationType, String payload, String error) {
        LocalDateTime now = LocalDateTime.now();
        SysSyncRetry existing = null;
        if (messageKey != null) {
            existing = syncRetryMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(SysSyncRetryTableDef.SYS_SYNC_RETRY.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                    .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.MESSAGE_KEY.eq(messageKey))
                    .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.DELETE_FLAG.eq(0))
            );
        }
        if (existing != null) {
            existing.setRetryCount(existing.getRetryCount() + 1);
            existing.setLastError(truncate(error, 500));
            existing.setNextRetryAt(now.plusMinutes(Math.min(existing.getRetryCount() * 5, 60)));
            existing.setStatus(existing.getRetryCount() >= existing.getMaxRetries() ? "exhausted" : "pending");
            existing.setUpdatedAt(now);
            syncRetryMapper.update(existing);
        } else {
            SysSyncRetry record = new SysSyncRetry();
            record.setMessageKey(messageKey);
            record.setTargetService(targetService);
            record.setEntityType(entityType);
            record.setExternalId(externalId);
            record.setOperationType(operationType);
            record.setPayload(payload);
            record.setRetryCount(0);
            record.setMaxRetries(DEFAULT_MAX_RETRIES);
            record.setNextRetryAt(now.plusMinutes(1));
            record.setStatus("pending");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setDeleteFlag(0L);
            syncRetryMapper.insert(record);
        }
    }

    @Override
    @Transactional
    public void markSuccess(Long id) {
        // Permission check - instance-level UPDATE on SYNC_RETRY
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_RETRY, id.toString(), AdminOperationCode.UPDATE);

        SysSyncRetry record = TenantSafeQuery.selectOneByIdSafe(
            syncRetryMapper, SysSyncRetryTableDef.SYS_SYNC_RETRY.ID, SysSyncRetryTableDef.SYS_SYNC_RETRY.TENANT_ID, SysSyncRetryTableDef.SYS_SYNC_RETRY.DELETE_FLAG,
            TenantContextHolder.getTenantId(), id);
        if (record != null) {
            record.setStatus("success");
            record.setUpdatedAt(LocalDateTime.now());
            syncRetryMapper.update(record);
        }
    }

    @Override
    @Transactional
    public void markFailed(Long id, String error) {
        // Permission check - instance-level UPDATE on SYNC_RETRY
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_RETRY, id.toString(), AdminOperationCode.UPDATE);

        recordSyncFailure(
            null, null, null, null, null, null, error
        );
        SysSyncRetry record = TenantSafeQuery.selectOneByIdSafe(
            syncRetryMapper, SysSyncRetryTableDef.SYS_SYNC_RETRY.ID, SysSyncRetryTableDef.SYS_SYNC_RETRY.TENANT_ID, SysSyncRetryTableDef.SYS_SYNC_RETRY.DELETE_FLAG,
            TenantContextHolder.getTenantId(), id);
        if (record != null) {
            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastError(truncate(error, 500));
            record.setNextRetryAt(LocalDateTime.now().plusMinutes(Math.min(record.getRetryCount() * 5, 60)));
            record.setStatus(record.getRetryCount() >= record.getMaxRetries() ? "exhausted" : "pending");
            record.setUpdatedAt(LocalDateTime.now());
            syncRetryMapper.update(record);
        }
    }

    @Override
    public List<SysSyncRetry> getPendingRetries() {
        return syncRetryMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysSyncRetryTableDef.SYS_SYNC_RETRY.STATUS.eq("pending"))
                .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.RETRY_COUNT.lt(SysSyncRetryTableDef.SYS_SYNC_RETRY.MAX_RETRIES))
                .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.NEXT_RETRY_AT.le(LocalDateTime.now()))
                .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.DELETE_FLAG.eq(0))
                .orderBy(SysSyncRetryTableDef.SYS_SYNC_RETRY.CREATED_AT.asc())
        );
    }

    @Override
    @Transactional
    public void deleteProcessed(Long id) {
        // Permission check - instance-level DELETE on SYNC_RETRY
        permissionValidator.checkInstanceLevel(AdminResourceType.SYNC_RETRY, id.toString(), AdminOperationCode.DELETE);

        SysSyncRetry record = TenantSafeQuery.selectOneByIdSafe(
            syncRetryMapper, SysSyncRetryTableDef.SYS_SYNC_RETRY.ID, SysSyncRetryTableDef.SYS_SYNC_RETRY.TENANT_ID, SysSyncRetryTableDef.SYS_SYNC_RETRY.DELETE_FLAG,
            TenantContextHolder.getTenantId(), id);
        if (record != null) {
            record.setDeleteFlag(record.getId());
            record.setDeletedAt(LocalDateTime.now());
            syncRetryMapper.update(record);
        }
    }

    @Override
    public PaginatedResult<SysSyncRetry> page(PageReq pageReq) {
        Page<SysSyncRetry> page = syncRetryMapper.paginate(
            Page.of(pageReq.getPageNum(), pageReq.getPageSize()),
            QueryWrapper.create()
                .where(SysSyncRetryTableDef.SYS_SYNC_RETRY.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SysSyncRetryTableDef.SYS_SYNC_RETRY.DELETE_FLAG.eq(0))
                .orderBy(SysSyncRetryTableDef.SYS_SYNC_RETRY.CREATED_AT.desc())
        );
        long totalPages = (page.getTotalRow() + pageReq.getPageSize() - 1) / pageReq.getPageSize();
        return new PaginatedResult<>(page.getRecords(),
            new PaginatedResult.PaginationMeta(page.getTotalRow(), pageReq.getPageNum(), pageReq.getPageSize(), (int) totalPages));
    }

    /**
     * Scheduled task that retries pending sync failures.
     * Runs every 30 seconds, once per active tenant.
     *
     * <p>{@code @TenantAwareScheduled} ensures tenant context is set for each iteration.
     */
    @TenantAwareScheduled
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void processRetries() {
        List<SysSyncRetry> pending = getPendingRetries();
        for (SysSyncRetry record : pending) {
            try {
                String url = buildUrl(record);
                if (url == null || record.getPayload() == null) {
                    log.warn("Skipping retry: missing url or payload for key={}", record.getMessageKey());
                    record.setStatus("exhausted");
                    record.setUpdatedAt(LocalDateTime.now());
                    syncRetryMapper.update(record);
                    continue;
                }

                log.info("Retrying sync: key={}, attempt={}/{}", record.getMessageKey(), record.getRetryCount() + 1, record.getMaxRetries());
                record.setRetryCount(record.getRetryCount() + 1);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(record.getPayload(), headers);

                String response = restTemplate.postForObject(url, entity, String.class);
                if (response != null) {
                    record.setStatus("success");
                    record.setUpdatedAt(LocalDateTime.now());
                    syncRetryMapper.update(record);
                    log.info("Sync retry succeeded: key={}", record.getMessageKey());
                }
            } catch (Exception e) {
                log.error("Sync retry failed: key={}", record.getMessageKey(), e);
                record.setLastError(truncate(e.getMessage(), 500));
                record.setNextRetryAt(LocalDateTime.now().plusMinutes(Math.min(record.getRetryCount() * 5, 60)));
                if (record.getRetryCount() >= record.getMaxRetries()) {
                    record.setStatus("exhausted");
                } else {
                    record.setStatus("pending");
                }
                record.setUpdatedAt(LocalDateTime.now());
                syncRetryMapper.update(record);
            }
        }
    }

    private String buildUrl(SysSyncRetry record) {
        String target = record.getTargetService();
        if (target == null || target.isBlank()) {
            return null;
        }
        // If target already contains protocol, use as-is
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target + "/" + record.getOperationType();
        }
        // Use lb:// protocol for service discovery
        String serviceUrl = target.startsWith("lb://") ? target : "lb://" + target;
        return serviceUrl + "/api/sync/" + record.getOperationType();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
