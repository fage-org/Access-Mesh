package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.SyncRetryMarkFailedReq;
import cn.ac.fage.accessmesh.admin.dto.resp.SyncRetryResp;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sync retry management API — view and manage failed sync records.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/sync-retry")
public class SyncRetryController {

    private final SyncRetryService syncRetryService;

    public SyncRetryController(SyncRetryService syncRetryService) {
        this.syncRetryService = syncRetryService;
    }

    /**
     * Paginated list of sync retry records.
     */
    @PostMapping("/page")
    public PermResult<PaginatedResult<SyncRetryResp>> page(@RequestBody PageReq pageReq) {
        PaginatedResult<SysSyncRetry> result = syncRetryService.page(pageReq);
        List<SyncRetryResp> items = result.items().stream()
            .map(SyncRetryResp::from)
            .toList();
        return PermResult.success(new PaginatedResult<>(items, result.pagination()));
    }

    /**
     * List pending retry records (status = pending).
     */
    @PostMapping("/pending")
    public PermResult<List<SyncRetryResp>> listPending() {
        List<SysSyncRetry> retries = syncRetryService.getPendingRetries();
        return PermResult.success(retries.stream().map(SyncRetryResp::from).toList());
    }

    /**
     * Mark a record as successfully synced.
     */
    @PostMapping("/mark-success")
    public PermResult<Void> markSuccess(@Valid @RequestBody IdReq req) {
        syncRetryService.markSuccess(req.id());
        return PermResult.success();
    }

    /**
     * Mark a record as failed with an error message.
     */
    @PostMapping("/mark-failed")
    public PermResult<Void> markFailed(@RequestBody SyncRetryMarkFailedReq req) {
        syncRetryService.markFailed(req.id(), req.error());
        return PermResult.success();
    }

    /**
     * Delete a processed sync retry record.
     */
    @PostMapping("/delete")
    public PermResult<Void> delete(@Valid @RequestBody IdsReq req) {
        for (Long id : req.ids()) {
            syncRetryService.deleteProcessed(id);
        }
        return PermResult.success();
    }
}
