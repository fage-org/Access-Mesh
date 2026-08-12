package cn.ac.fage.accessmesh.access.admin.dto.resp;

import java.time.LocalDateTime;

/**
 * 通知公告响应记录类
 * <p>
 * 用于返回通知公告详情信息。
 * 包含标题、内容、通知类型、目标用户、状态、创建时间、更新时间。
 * </p>
 *
 * @param id             通知ID
 * @param title          公告标题
 * @param content        公告内容（富文本）
 * @param noticeType     通知类型（1=通知，2=公告）
 * @param targetUserIds  目标用户ID列表（逗号分隔）
 * @param status         状态（0=正常，1=禁用）
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
     * 公告内容（富文本）
     */
    String content,

    /**
     * 通知类型（1=通知，2=公告）
     */
    Integer noticeType,

    /**
     * 目标用户ID列表（逗号分隔）
     */
    String targetUserIds,

    /**
     * 状态（0=正常，1=禁用）
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