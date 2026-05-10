package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * 审计日志服务接口
 * <p>
 * 提供审计日志的查询功能。
 * 审计日志记录用户的操作行为，用于安全审计和问题追溯。
 * </p>
 */
public interface AuditLogService {

    /**
     * 分页查询审计日志
     * <p>
     * 查询系统中的审计日志记录，支持分页。
     * </p>
     *
     * @param pageReq 分页请求参数
     * @return 分页审计日志结果
     */
    PaginatedResult<SysAuditLog> pageAuditLogs(PageReq pageReq);
}
