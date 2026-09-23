package cn.ac.fage.accessmesh.access.platform.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知公告响应记录类（T-ADMIN-029：typed IDs 一次性切换，零兼容层）
 * <p>
 * 用于返回通知公告详情信息。
 * 包含标题、内容、通知类型、目标受众、状态、创建时间、更新时间。
 * </p>
 *
 * @param id             通知ID
 * @param title          公告标题
 * @param content        公告内容
 * @param noticeType     通知类型（1=通知，2=公告）
 * @param targetType     目标类型（ALL=全员，USER=指定用户）
 * @param targetUserIds  目标用户 ID 列表（targetType=USER 时非空）
 * @param status         状态（0=草稿，1=已发布，2=已撤回）
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record NoticeResp(
    /**
     * 通知ID
     */
    Long id,

    /**
     * 公告标题
     */
    String title,

    /**
     * 公告内容
     */
    String content,

    /**
     * 通知类型（1=通知，2=公告）
     */
    Integer noticeType,

    /**
     * 目标类型（ALL=全员，USER=指定用户）
     */
    String targetType,

    /**
     * 目标用户 ID 列表（targetType=USER 时非空）
     */
    List<Long> targetUserIds,

    /**
     * 状态（0=草稿，1=已发布，2=已撤回）
     */
    Integer status,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {}
