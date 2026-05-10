package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.NoticeUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.NoticeResp;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * 通知公告服务接口
 * <p>
 * 提供通知公告管理相关的服务方法，包括通知的创建、更新、删除、发布等。
 * 支持通知的阅读状态追踪和用户通知列表查询。
 * </p>
 */
public interface NoticeService {

    /**
     * 创建通知
     * <p>
     * 创建新的通知公告。
     * 创建后通知为未发布状态，需要手动发布。
     * </p>
     *
     * @param req 通知创建请求
     * @return 创建的通知ID
     */
    Long createNotice(NoticeCreateReq req);

    /**
     * 更新通知
     * <p>
     * 更新指定通知的基本信息。
     * 包括通知标题、内容、类型等属性。
     * </p>
     *
     * @param req 通知更新请求
     */
    void updateNotice(NoticeUpdateReq req);

    /**
     * 删除通知
     * <p>
     * 批量删除多个通知公告。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param req 待删除的通知ID列表请求
     */
    void deleteNotice(IdsReq req);

    /**
     * 获取通知详情
     * <p>
     * 根据ID查询通知详细信息。
     * </p>
     *
     * @param id 通知ID
     * @return 通知详情响应
     */
    NoticeResp getNotice(Long id);

    /**
     * 分页查询通知列表
     * <p>
     * 根据条件分页查询通知列表。
     * 支持按通知类型、标题等条件筛选。
     * </p>
     *
     * @param pageReq 分页查询请求
     * @return 分页通知列表结果
     */
    PaginatedResult<NoticeResp> pageNotices(PageReq pageReq);

    /**
     * 发布通知
     * <p>
     * 将通知状态设置为已发布。
     * 发布后通知对用户可见。
     * </p>
     *
     * @param id 通知ID
     */
    void publishNotice(Long id);

    /**
     * 标记通知为已读
     * <p>
     * 记录用户已阅读指定通知的状态。
     * 用于追踪通知的阅读情况。
     * </p>
     *
     * @param noticeId 通知ID
     * @param userId   用户ID
     */
    void markNoticeAsRead(Long noticeId, Long userId);

    /**
     * 获取用户的通知列表（含阅读状态）
     * <p>
     * 查询指定用户的所有通知及其阅读状态。
     * 用于用户的未读通知提醒和通知列表展示。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户通知列表（含阅读状态）
     */
    java.util.List<UserNoticeItem> listMyNotices(Long userId);

    /**
     * 用户通知条目记录类
     * <p>
     * 用于封装用户通知列表的条目信息，包含通知内容和阅读状态。
     * </p>
     */
    record UserNoticeItem(Long noticeId, String title, String content, String noticeType,
                          java.time.LocalDateTime createdAt, Boolean isRead,
                          java.time.LocalDateTime readAt) {}
}