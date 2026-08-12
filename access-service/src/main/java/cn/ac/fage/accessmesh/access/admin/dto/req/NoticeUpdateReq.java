package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 通知公告更新请求记录类
 * <p>
 * 用于更新通知公告的请求参数。
 * 标题和内容为必填项，其他字段可选。
 * </p>
 *
 * @param id             公告ID（必填，用于定位公告）
 * @param title          公告标题（必填）
 * @param content        公告内容（必填，富文本）
 * @param noticeType     通知类型（可选，1=通知，2=公告）
 * @param targetUserIds  目标用户ID列表（可选，逗号分隔）
 */
public record NoticeUpdateReq(
    /**
     * 公告ID
     */
    @NotNull(message = "公告ID不能为空")
    Long id,

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    String title,

    /**
     * 公告内容（富文本）
     */
    @NotBlank(message = "公告内容不能为空")
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