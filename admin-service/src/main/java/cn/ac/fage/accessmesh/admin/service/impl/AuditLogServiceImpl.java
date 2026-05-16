package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.admin.mapper.SysAuditLogMapper;
import cn.ac.fage.accessmesh.admin.service.AuditLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 审计日志服务实现类
 * <p>
 * 提供审计日志的分页查询功能。
 * 审计日志记录用户操作行为，用于安全审计和合规性检查。
 * 通过@AuditLog注解自动记录操作日志。
 * 日志按租户隔离，按创建时间倒序排列。
 * </p>
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    /**
     * 构造函数注入依赖
     *
     * @param auditLogMapper 审计日志数据访问Mapper
     */
    public AuditLogServiceImpl(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 分页查询审计日志列表
     * <p>
     * 获取当前租户的审计日志，按创建时间倒序排列。
     * 用于查看用户操作历史，进行安全审计。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页审计日志列表结果
     */
    @Override
    public PaginatedResult<SysAuditLog> pageAuditLogs(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<SysAuditLog> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysAuditLog> result = auditLogMapper.paginateByTenantId(page, tenantId);

        List<SysAuditLog> items = result.getRecords();
        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }
}