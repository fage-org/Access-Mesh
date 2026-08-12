package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 通知公告创建请求记录类
 * <p>
 * 用于创建新通知公告的请求参数。
 * 包含标题、内容、通知类型、目标用户等。
 * </p>
 *
 * @param title         公告标题（必填）
 * @param content       公告内容（可选）
 * @param noticeType    通知类型（可选，1=通知，2=公告）
 * @param targetUserIds 目标用户ID列表（可选，逗号分隔）
 */
public record NoticeCreateReq(
    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
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
     * 目标用户ID列表（逗号分隔，空表示全员）
     */
    String targetUserIds
) {}