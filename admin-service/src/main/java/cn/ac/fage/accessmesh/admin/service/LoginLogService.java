package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface LoginLogService {

    PaginatedResult<SysLoginLog> pageLoginLogs(PageReq pageReq);
}
