package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ChangeLogListReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationLogListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.service.LogQueryService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Log query API - combines change log and operation log.
 */
@RestController
@RequestMapping("/api/perm/log")
public class LogQueryController {

    private final LogQueryService logQueryService;

    public LogQueryController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    // ===== ChangeLog =====

    @PostMapping("/change/list")
    public PermResult<PaginatedResp<ChangeLogResp>> listChangeLogs(@Valid @RequestBody ChangeLogListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = logQueryService.countChangeLogs(tenantId, req.entityType(), req.entityId());
        List<ChangeLogResp> items = logQueryService.listChangeLogs(
                tenantId, req.entityType(), req.entityId(),
                offset, pageSize);
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    // ===== OperationLog =====

    @PostMapping("/operation/list")
    public PermResult<PaginatedResp<OperationLogResp>> listOperationLogs(@Valid @RequestBody OperationLogListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = logQueryService.countOperationLogs(tenantId, req.module(), req.action());
        List<OperationLogResp> items = logQueryService.listOperationLogs(
                tenantId, req.module(), req.action(),
                offset, pageSize);
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}