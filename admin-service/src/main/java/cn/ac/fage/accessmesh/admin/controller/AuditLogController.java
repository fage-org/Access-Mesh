package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.AuditLogResp;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.admin.service.AuditLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<AuditLogResp>> pageAuditLogs(@RequestBody PageReq pageReq) {
        PaginatedResult<SysAuditLog> result = auditLogService.pageAuditLogs(pageReq);
        List<AuditLogResp> items = result.items().stream()
            .map(AuditLogResp::from)
            .toList();
        return PermResult.success(new PaginatedResult<>(items, result.pagination()));
    }
}
