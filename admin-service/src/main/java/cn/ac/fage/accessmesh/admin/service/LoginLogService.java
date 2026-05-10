package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * 登录日志服务接口
 * <p>
 * 提供登录日志的查询功能。
 * 登录日志记录用户的登录行为，包括登录时间、IP、状态等。
 * </p>
 */
public interface LoginLogService {

    /**
     * 分页查询登录日志
     * <p>
     * 查询系统中的登录日志记录，支持分页。
     * </p>
     *
     * @param pageReq 分页请求参数
     * @return 分页登录日志结果
     */
    PaginatedResult<SysLoginLog> pageLoginLogs(PageReq pageReq);
}
