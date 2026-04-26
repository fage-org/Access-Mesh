package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.admin.entity.table.SysAuditLogTableDef;
import cn.ac.fage.accessmesh.admin.mapper.SysAuditLogMapper;
import cn.ac.fage.accessmesh.admin.service.AuditLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.ac.fage.accessmesh.admin.entity.table.SysAuditLogTableDef.SYS_AUDIT_LOG;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public PaginatedResult<SysAuditLog> pageAuditLogs(PageReq pageReq) {
        Page<SysAuditLog> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysAuditLog> result = auditLogMapper.paginate(page,
            QueryWrapper.create()
                .orderBy(SYS_AUDIT_LOG.CREATED_AT.desc()));

        List<SysAuditLog> items = result.getRecords();
        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }
}
