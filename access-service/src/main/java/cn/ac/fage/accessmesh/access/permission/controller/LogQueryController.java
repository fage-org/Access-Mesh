package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.ChangeLogListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.LogActionOptionsReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationLogListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.access.permission.service.LogQueryAppService;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日志查询控制器
 * <p>
 * 提供变更日志和操作日志的查询功能。
 * 变更日志记录实体数据的变更历史，用于数据审计。
 * 操作日志记录用户操作行为，用于行为追踪。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/log")
public class LogQueryController {

    private final LogQueryAppService logQueryService;

    /**
     * 构造函数注入依赖
     *
     * @param logQueryService 日志查询服务
     */
    public LogQueryController(LogQueryAppService logQueryService) {
        this.logQueryService = logQueryService;
    }

    // ===== 变更日志 =====

    /**
     * 分页查询变更日志列表
     * <p>
     * 查询实体数据的变更历史记录。
     * 支持按实体类型和实体ID过滤。
     * 用于数据审计和变更追溯。
     * </p>
     *
     * @param req 变更日志查询请求，包含实体类型、实体ID、分页参数
     * @return 分页变更日志列表结果
     */
    @PostMapping("/change/list")
    public R<PaginatedResp<ChangeLogResp>> listChangeLogs(@Valid @RequestBody ChangeLogListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = logQueryService.countChangeLogs(tenantId, req.entityType(), req.entityId(),
                req.eventType(), req.changeSource(), req.affectedUserId(), req.affectedRoleId(),
                req.since(), req.until());
        List<ChangeLogResp> items = logQueryService.listChangeLogs(
                tenantId, req.entityType(), req.entityId(),
                req.eventType(), req.changeSource(), req.affectedUserId(), req.affectedRoleId(),
                req.since(), req.until(), offset, pageSize);
        return R.ok(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    // ===== 操作日志 =====

    /**
     * 分页查询操作日志列表
     * <p>
     * 查询用户的操作行为记录。
     * 支持按模块、操作、操作者、时间范围、目标类型过滤。
     * 用于行为追踪和合规审计。
     * </p>
     *
     * @param req 操作日志查询请求，包含过滤条件与分页参数
     * @return 分页操作日志列表结果
     */
    @PostMapping("/operation/list")
    public R<PaginatedResp<OperationLogResp>> listOperationLogs(@Valid @RequestBody OperationLogListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = logQueryService.countOperationLogs(tenantId, req.module(), req.action(),
            req.operatorId(), req.since(), req.until(), req.targetType());
        List<OperationLogResp> items = logQueryService.listOperationLogs(
                tenantId, req.module(), req.action(),
                req.operatorId(), req.since(), req.until(), req.targetType(),
                offset, pageSize);
        return R.ok(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    /**
     * 查询操作日志 action 字典
     * <p>
     * 返回 operation_log 当前实际存在的 action 去重集合（按 module 可选过滤），
     * 供前端筛选下拉动态拉取（T-PERM-025）。
     * </p>
     *
     * @param req 字典查询请求，含可选模块过滤
     * @return action 去重集合（字典序）
     */
    @PostMapping("/operation/action-options")
    public R<ItemsResp<String>> listActionOptions(@Valid @RequestBody LogActionOptionsReq req) {
        return R.ok(new ItemsResp<>(
            logQueryService.listActionOptions(TenantContextHolder.getTenantId(), req.module())));
    }
}