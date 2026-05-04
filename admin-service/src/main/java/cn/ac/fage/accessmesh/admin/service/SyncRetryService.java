package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.util.List;

public interface SyncRetryService {

    void recordSyncFailure(String messageKey, String targetService, String entityType,
                           String externalId, String operationType, String payload, String error);

    void markSuccess(Long id);

    void markFailed(Long id, String error);

    List<SysSyncRetry> getPendingRetries();

    void deleteProcessed(Long id);

    PaginatedResult<SysSyncRetry> page(PageReq pageReq);
}
