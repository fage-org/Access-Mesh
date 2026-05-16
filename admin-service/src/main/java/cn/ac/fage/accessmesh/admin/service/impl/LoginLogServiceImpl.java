package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.admin.service.LoginLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 登录日志服务实现类
 * <p>
 * 提供登录日志的分页查询功能。
 * 登录日志记录用户登录成功/失败事件，用于安全审计和问题排查。
 * 日志按租户隔离，按登录时间倒序排列。
 * </p>
 */
@Service
public class LoginLogServiceImpl implements LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    /**
     * 构造函数注入依赖
     *
     * @param loginLogMapper 登录日志数据访问Mapper
     */
    public LoginLogServiceImpl(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 分页查询登录日志列表
     * <p>
     * 获取当前租户的登录日志，按登录时间倒序排列。
     * 用于查看用户登录历史，排查登录问题。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @return 分页登录日志列表结果
     */
    @Override
    public PaginatedResult<SysLoginLog> pageLoginLogs(PageReq pageReq) {
        Long tenantId = TenantContextHolder.getTenantId();
        Page<SysLoginLog> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysLoginLog> result = loginLogMapper.paginateByTenantId(page, tenantId);

        List<SysLoginLog> items = result.getRecords();
        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }
}