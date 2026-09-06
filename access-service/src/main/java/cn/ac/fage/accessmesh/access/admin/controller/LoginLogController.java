package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.LoginLogResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.access.admin.service.LoginLogService;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录日志控制器
 * <p>
 * 提供登录日志的分页查询功能。
 * 登录日志记录用户的登录行为，包括登录时间、IP地址、登录状态等。
 * 用于安全审计和登录行为分析。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/login-log")
public class LoginLogController {

    private final LoginLogService loginLogService;

    /**
     * 构造函数注入依赖
     *
     * @param loginLogService 登录日志服务
     */
    public LoginLogController(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    /**
     * 分页查询登录日志列表
     * <p>
     * 查询用户的登录记录，支持分页。
     * 用于安全审计和登录行为分析。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页登录日志列表结果
     */
    @PostMapping("/page")
    public R<PageResp<LoginLogResp>> pageLoginLogs(@Valid @RequestBody PageReq pageReq) {
        PageResp<SysLoginLog> result = loginLogService.pageLoginLogs(pageReq);
        List<LoginLogResp> items = result.items().stream()
            .map(LoginLogResp::from)
            .toList();
        return R.ok(new PageResp<>(items, result.total(), result.pageNum(), result.pageSize(), result.hasNext()));
    }
}