package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.NoticeUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.access.admin.service.NoticeService;
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

/**
 * 公告管理控制器
 * <p>
 * 提供公告的CRUD操作、发布、阅读标记、用户公告查询等功能。
 * 公告用于向用户发布系统通知、重要信息等。
 * 支持公告发布后用户查看并标记已读状态。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 构造函数注入依赖
     *
     * @param noticeService 公告管理服务
     */
    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /**
     * 创建公告
     * <p>
     * 创建新的公告，设置公告标题、内容、类型等属性。
     * 创建后公告为草稿状态，需发布后才对用户可见。
     * </p>
     *
     * @param req 公告创建请求，包含公告基本信息
     * @return 创建成功的公告ID
     */
    @PostMapping("/create")
    @AuditLog(module = "公告管理", action = "创建", targetType = "NOTICE")
    public PermResult<Long> createNotice(@Valid @RequestBody NoticeCreateReq req) {
        return PermResult.success(noticeService.createNotice(req));
    }

    /**
     * 更新公告信息
     * <p>
     * 更新公告的标题、内容、类型等属性。
     * </p>
     *
     * @param req 公告更新请求，包含公告ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    @AuditLog(module = "公告管理", action = "修改", targetType = "NOTICE")
    public PermResult<Void> updateNotice(@Valid @RequestBody NoticeUpdateReq req) {
        noticeService.updateNotice(req);
        return PermResult.success();
    }

    /**
     * 删除公告
     * <p>
     * 批量删除公告，会同时处理用户已读记录。
     * </p>
     *
     * @param req ID集合请求，包含待删除的公告ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    @AuditLog(module = "公告管理", action = "删除", targetType = "NOTICE")
    public PermResult<Void> deleteNotice(@Valid @RequestBody IdsReq req) {
        noticeService.deleteNotice(req);
        return PermResult.success();
    }

    /**
     * 获取公告详情
     * <p>
     * 根据公告ID查询公告的完整信息。
     * </p>
     *
     * @param req ID请求，包含公告ID
     * @return 公告详情信息
     */
    @PostMapping("/detail")
    public PermResult<NoticeResp> getNotice(@Valid @RequestBody IdReq req) {
        return PermResult.success(noticeService.getNotice(req.id()));
    }

    /**
     * 分页查询公告列表
     * <p>
     * 查询系统公告列表，支持分页。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页公告列表结果
     */
    @PostMapping("/page")
    public PermResult<PaginatedResult<NoticeResp>> pageNotices(@Valid @RequestBody PageReq pageReq) {
        return PermResult.success(noticeService.pageNotices(pageReq));
    }

    /**
     * 发布公告
     * <p>
     * 将草稿状态的公告发布，发布后公告对用户可见。
     * </p>
     *
     * @param req ID请求，包含公告ID
     * @return 操作成功结果
     */
    @PostMapping("/publish")
    @AuditLog(module = "公告管理", action = "发布", targetType = "NOTICE")
    public PermResult<Void> publishNotice(@Valid @RequestBody IdReq req) {
        noticeService.publishNotice(req.id());
        return PermResult.success();
    }

    /**
     * 标记公告已读
     * <p>
     * 用户阅读公告后标记为已读状态。
     * 用于统计公告阅读情况。
     * </p>
     *
     * @param req ID请求，包含公告ID
     * @return 操作成功结果
     */
    @PostMapping("/read")
    public PermResult<Void> markAsRead(@Valid @RequestBody IdReq req) {
        noticeService.markNoticeAsRead(req.id(), StpUtil.getLoginIdAsLong());
        return PermResult.success();
    }

    /**
     * 查询用户公告列表
     * <p>
     * 查询当前用户可见的公告列表，包含已读状态。
     * 用于用户公告中心展示。
     * </p>
     *
     * @return 用户公告列表，包含公告信息和已读状态
     */
    @PostMapping("/my-notices")
    public PermResult<java.util.List<NoticeService.UserNoticeItem>> listMyNotices() {
        return PermResult.success(noticeService.listMyNotices(StpUtil.getLoginIdAsLong()));
    }
}