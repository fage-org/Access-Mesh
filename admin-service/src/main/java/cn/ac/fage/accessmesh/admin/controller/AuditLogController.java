package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.admin.service.AuditLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<SysAuditLog>> pageAuditLogs(@RequestBody PageReq pageReq) {
        return PermResult.success(auditLogService.pageAuditLogs(pageReq));
    }
}
