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
 * 同步重试管理控制器
 * <p>
 * 提供同步失败记录的查询、状态更新、删除等功能。
 * 同步重试用于处理跨系统数据同步失败的情况，记录失败原因并支持重试。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/sync-retry")
public class SyncRetryController {

    private final SyncRetryService syncRetryService;

    /**
     * 构造函数注入依赖
     *
     * @param syncRetryService 同步重试服务
     */
    public SyncRetryController(SyncRetryService syncRetryService) {
        this.syncRetryService = syncRetryService;
    }

    /**
     * 分页查询同步重试记录列表
     * <p>
     * 查询所有同步重试记录，包括成功、失败、待重试等状态。
     * 用于监控数据同步状态和排查同步问题。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页同步重试记录列表结果
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
     * 查询待重试记录列表
     * <p>
     * 查询状态为待重试的同步记录，用于批量重试处理。
     * </p>
     *
     * @return 待重试记录列表
     */
    @PostMapping("/pending")
    public PermResult<List<SyncRetryResp>> listPending() {
        List<SysSyncRetry> retries = syncRetryService.getPendingRetries();
        return PermResult.success(retries.stream().map(SyncRetryResp::from).toList());
    }

    /**
     * 标记同步成功
     * <p>
     * 将同步记录标记为成功状态，表示数据同步已完成。
     * </p>
     *
     * @param req ID请求，包含同步记录ID
     * @return 操作成功结果
     */
    @PostMapping("/mark-success")
    public PermResult<Void> markSuccess(@Valid @RequestBody IdReq req) {
        syncRetryService.markSuccess(req.id());
        return PermResult.success();
    }

    /**
     * 标记同步失败
     * <p>
     * 将同步记录标记为失败状态，并记录错误原因。
     * 用于记录重试失败的情况。
     * </p>
     *
     * @param req 标记失败请求，包含同步记录ID和错误信息
     * @return 操作成功结果
     */
    @PostMapping("/mark-failed")
    public PermResult<Void> markFailed(@RequestBody SyncRetryMarkFailedReq req) {
        syncRetryService.markFailed(req.id(), req.error());
        return PermResult.success();
    }

    /**
     * 删除已处理的同步记录
     * <p>
     * 删除已完成（成功或放弃）的同步记录，清理历史数据。
     * </p>
     *
     * @param req ID集合请求，包含待删除的同步记录ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    public PermResult<Void> delete(@Valid @RequestBody IdsReq req) {
        for (Long id : req.ids()) {
            syncRetryService.deleteProcessed(id);
        }
        return PermResult.success();
    }
}