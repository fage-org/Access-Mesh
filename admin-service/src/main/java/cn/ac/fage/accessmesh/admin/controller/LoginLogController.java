package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.LoginLogResp;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.admin.service.LoginLogService;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/login-log")
public class LoginLogController {

    private final LoginLogService loginLogService;

    public LoginLogController(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<LoginLogResp>> pageLoginLogs(@RequestBody PageReq pageReq) {
        PaginatedResult<SysLoginLog> result = loginLogService.pageLoginLogs(pageReq);
        List<LoginLogResp> items = result.items().stream()
            .map(LoginLogResp::from)
            .toList();
        return PermResult.success(new PaginatedResult<>(items, result.pagination()));
    }
}
