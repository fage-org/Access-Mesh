package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.admin.service.NoticeService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping("/create")
    @AuditLog(module = "公告管理", action = "创建", targetType = "NOTICE")
    public PermResult<Long> createNotice(@Valid @RequestBody NoticeCreateReq req) {
        return PermResult.success(noticeService.createNotice(req));
    }

    @PostMapping("/update")
    @AuditLog(module = "公告管理", action = "修改", targetType = "NOTICE")
    public PermResult<Void> updateNotice(@Valid @RequestBody NoticeCreateReq req) {
        noticeService.updateNotice(req);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "公告管理", action = "删除", targetType = "NOTICE")
    public PermResult<Void> deleteNotice(@Valid @RequestBody IdsReq req) {
        noticeService.deleteNotice(req);
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<NoticeResp> getNotice(@Valid @RequestBody IdReq req) {
        return PermResult.success(noticeService.getNotice(req.id()));
    }

    @PostMapping("/page")
    public PermResult<PaginatedResult<NoticeResp>> pageNotices(@Valid @RequestBody PageReq pageReq) {
        return PermResult.success(noticeService.pageNotices(pageReq));
    }

    @PostMapping("/publish")
    @AuditLog(module = "公告管理", action = "发布", targetType = "NOTICE")
    public PermResult<Void> publishNotice(@Valid @RequestBody IdReq req) {
        noticeService.publishNotice(req.id());
        return PermResult.success();
    }

    @PostMapping("/read")
    public PermResult<Void> markAsRead(@Valid @RequestBody IdReq req) {
        noticeService.markNoticeAsRead(req.id(), StpUtil.getLoginIdAsLong());
        return PermResult.success();
    }

    @PostMapping("/my-notices")
    public PermResult<java.util.List<NoticeService.UserNoticeItem>> listMyNotices() {
        return PermResult.success(noticeService.listMyNotices(StpUtil.getLoginIdAsLong()));
    }
}
