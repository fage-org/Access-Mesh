package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.admin.entity.table.SysLoginLogTableDef;
import cn.ac.fage.accessmesh.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.admin.service.LoginLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class LoginLogServiceImpl implements LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    public LoginLogServiceImpl(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @Override
    public PaginatedResult<SysLoginLog> pageLoginLogs(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<SysLoginLog> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysLoginLog> result = loginLogMapper.paginate(page,
            QueryWrapper.create()
                .where(SysLoginLogTableDef.SYS_LOGIN_LOG.TENANT_ID.eq(tenantId))
                .orderBy(SysLoginLogTableDef.SYS_LOGIN_LOG.LOGIN_AT.desc()));

        List<SysLoginLog> items = result.getRecords();
        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }
}
