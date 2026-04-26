package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface AuditLogService {

    PaginatedResult<SysAuditLog> pageAuditLogs(PageReq pageReq);
}
