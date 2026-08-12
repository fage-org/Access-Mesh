package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.AuditLogResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.access.admin.service.AuditLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计日志控制器
 * <p>
 * 提供审计日志的分页查询功能。
 * 审计日志记录用户的操作行为，用于安全审计和行为追踪。
 * 日志包含操作模块、操作类型、操作对象、操作结果等信息。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 构造函数注入依赖
     *
     * @param auditLogService 审计日志服务
     */
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 分页查询审计日志列表
     * <p>
     * 查询用户的操作审计记录，支持分页。
     * 用于安全审计和合规检查。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页审计日志列表结果
     */
    @PostMapping("/page")
    public PermResult<PaginatedResult<AuditLogResp>> pageAuditLogs(@RequestBody PageReq pageReq) {
        PaginatedResult<SysAuditLog> result = auditLogService.pageAuditLogs(pageReq);
        List<AuditLogResp> items = result.items().stream()
            .map(AuditLogResp::from)
            .toList();
        return PermResult.success(new PaginatedResult<>(items, result.pagination()));
    }
}