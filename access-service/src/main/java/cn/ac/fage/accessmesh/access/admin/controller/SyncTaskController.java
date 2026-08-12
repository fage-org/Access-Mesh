package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskBatchStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskMarkFailedReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskQueryReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.SyncTaskRebuildFromFactReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskBatchStatusResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskDetailResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskRebuildResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.SyncTaskResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskBatchStatusReport;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskQueryParams;
import cn.ac.fage.accessmesh.access.admin.sync.orchestrator.SyncFullSyncOrchestrator;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同步任务管理控制器
 * <p>
 * 暴露 {@code sys_sync_task} 本地消息表的查询、状态更新、删除、补偿（retry-now/reset）、
 * 全量校准触发（rebuild-from-fact）、批次状态聚合（batch-status）等管理面接口。
 * 所有接口采用 POST + JSON Body 方式。
 * </p>
 * <p>
 * 入口级权限通过 {@link AdminPermissionValidator} 校验：资源类型 ADMIN_SYNC_TASK，
 * 当前实现以 UPDATE/VIEW 操作映射"管理"语义。
 * </p>
 */
@RestController
@RequestMapping("/sync-task")
public class SyncTaskController {

    private static final Logger log = LoggerFactory.getLogger(SyncTaskController.class);

    private final SyncTaskDomainService syncTaskDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final SyncFullSyncOrchestrator syncFullSyncOrchestrator;

    public SyncTaskController(SyncTaskDomainService syncTaskDomainService,
                              AdminPermissionValidator permissionValidator,
                              SyncFullSyncOrchestrator syncFullSyncOrchestrator) {
        this.syncTaskDomainService = syncTaskDomainService;
        this.permissionValidator = permissionValidator;
        this.syncFullSyncOrchestrator = syncFullSyncOrchestrator;
    }

    /**
     * 分页查询同步任务列表（保留旧接口）。
     */
    @PostMapping("/page")
    @AuditLog(module = "sync-task", action = "PAGE")
    public PermResult<PaginatedResult<SyncTaskResp>> page(@RequestBody PageReq pageReq) {
        permissionValidator.checkTypeLevel(AdminResourceType.SYNC_TASK, AdminOperationCode.VIEW);
        PaginatedResult<SysSyncTask> result = syncTaskDomainService.page(pageReq);
        List<SyncTaskResp> items = result.items().stream().map(SyncTaskResp::from).toList();
        return PermResult.success(new PaginatedResult<>(items, result.pagination()));
    }

    /**
     * 增强查询：按 syncAction / status / phase / batchKey / businessKey 过滤分页。
     */
    @PostMapping("/list")
    @AuditLog(module = "sync-task", action = "LIST")
    public PermResult<PaginatedResult<SyncTaskResp>> list(@RequestBody SyncTaskQueryReq req) {
        permissionValidator.checkTypeLevel(AdminResourceType.SYNC_TASK, AdminOperationCode.VIEW);
        SyncTaskQueryParams params = new SyncTaskQueryParams(
            null, // tenantId 由 service 注入
            req.syncAction(), req.status(), req.phase(), req.batchKey(), req.businessKey()
        );
        PaginatedResult<SysSyncTask> result =
            syncTaskDomainService.listEnhanced(params, req.safePageNum(), req.safePageSize());
        List<SyncTaskResp> items = result.items().stream().map(SyncTaskResp::from).toList();
        return PermResult.success(new PaginatedResult<>(items, result.pagination()));
    }

    /**
     * 查询任务详情（含 payload / displayAttrs / lastError）。
     */
    @PostMapping("/detail")
    @AuditLog(module = "sync-task", action = "DETAIL")
    public PermResult<SyncTaskDetailResp> detail(@Valid @RequestBody IdReq req) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.SYNC_TASK, req.id().toString(), AdminOperationCode.VIEW);
        SysSyncTask record = syncTaskDomainService.getByIdSafe(req.id());
        if (record == null) {
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "TASK_NOT_FOUND: id=" + req.id());
        }
        return PermResult.success(SyncTaskDetailResp.from(record));
    }

    /**
     * 立即重试（FAILED / PENDING 任务强制下次 tick 执行）。
     */
    @PostMapping("/retry-now")
    @AuditLog(module = "sync-task", action = "RETRY_NOW")
    public PermResult<Void> retryNow(@Valid @RequestBody IdReq req) {
        log.info("retry-now requested: taskId={}, tenantId={}",
            req.id(), TenantContextHolder.getTenantId());
        syncTaskDomainService.retryNow(req.id());
        return PermResult.success();
    }

    /**
     * 重置任务（清空 retry_count + last_error，回到 PENDING 初始状态）。
     */
    @PostMapping("/reset")
    @AuditLog(module = "sync-task", action = "RESET")
    public PermResult<Void> reset(@Valid @RequestBody IdReq req) {
        log.info("reset requested: taskId={}, tenantId={}",
            req.id(), TenantContextHolder.getTenantId());
        syncTaskDomainService.resetTask(req.id());
        return PermResult.success();
    }

    /**
     * 触发全量校准（生成新 batchKey）。
     */
    @PostMapping("/rebuild-from-fact")
    @AuditLog(module = "sync-task", action = "REBUILD_FROM_FACT")
    public PermResult<SyncTaskRebuildResp> rebuildFromFact(@Valid @RequestBody SyncTaskRebuildFromFactReq req) {
        permissionValidator.checkTypeLevel(AdminResourceType.SYNC_TASK, AdminOperationCode.UPDATE);
        Long tenantId = TenantContextHolder.getTenantId();
        log.info("rebuild-from-fact start: tenantId={}, sourceService={}, triggeredBy={}",
            tenantId, req.sourceService(), req.triggeredBy());
        String batchKey = syncFullSyncOrchestrator.startFullSyncRun(
            tenantId, req.sourceService(), req.triggeredBy());
        SyncTaskBatchStatusReport report = syncTaskDomainService.queryBatchStatus(batchKey);
        log.info("rebuild-from-fact done: tenantId={}, batchKey={}, taskCount={}",
            tenantId, batchKey, report.totalTasks());
        return PermResult.success(new SyncTaskRebuildResp(batchKey, report.totalTasks()));
    }

    /**
     * 查询某 batchKey 阶段进度。
     */
    @PostMapping("/batch-status")
    @AuditLog(module = "sync-task", action = "BATCH_STATUS")
    public PermResult<SyncTaskBatchStatusResp> batchStatus(@Valid @RequestBody SyncTaskBatchStatusReq req) {
        permissionValidator.checkTypeLevel(AdminResourceType.SYNC_TASK, AdminOperationCode.VIEW);
        SyncTaskBatchStatusReport report = syncTaskDomainService.queryBatchStatus(req.batchKey());
        return PermResult.success(new SyncTaskBatchStatusResp(
            report.batchKey(), report.totalTasks(), report.statusCounts(),
            report.phaseCounts(), report.currentPhase(), report.hasFailed()
        ));
    }

    /**
     * 查询到期可执行的任务列表（保留旧接口）。
     */
    @PostMapping("/due")
    @AuditLog(module = "sync-task", action = "DUE")
    public PermResult<List<SyncTaskResp>> listDue() {
        permissionValidator.checkTypeLevel(AdminResourceType.SYNC_TASK, AdminOperationCode.VIEW);
        List<SysSyncTask> tasks = syncTaskDomainService.getDueTasks();
        return PermResult.success(tasks.stream().map(SyncTaskResp::from).toList());
    }

    /**
     * 标记任务执行成功。
     */
    @PostMapping("/mark-success")
    @AuditLog(module = "sync-task", action = "MARK_SUCCESS")
    public PermResult<Void> markSuccess(@Valid @RequestBody IdReq req) {
        syncTaskDomainService.markSuccess(req.id());
        return PermResult.success();
    }

    /**
     * 标记任务执行失败（更新错误信息与重试次数）。
     */
    @PostMapping("/mark-failed")
    @AuditLog(module = "sync-task", action = "MARK_FAILED")
    public PermResult<Void> markFailed(@Valid @RequestBody SyncTaskMarkFailedReq req) {
        syncTaskDomainService.markFailed(req.id(), req.error());
        return PermResult.success();
    }

    /**
     * 软删除已处理任务（SUCCESS / FAILED 终态记录）。
     */
    @PostMapping("/delete")
    @AuditLog(module = "sync-task", action = "DELETE")
    public PermResult<Void> delete(@Valid @RequestBody IdsReq req) {
        for (Long id : req.ids()) {
            syncTaskDomainService.deleteProcessed(id);
        }
        return PermResult.success();
    }
}
