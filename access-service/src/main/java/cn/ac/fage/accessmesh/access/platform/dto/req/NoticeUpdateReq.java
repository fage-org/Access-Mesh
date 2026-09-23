package cn.ac.fage.accessmesh.access.platform.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 通知公告更新请求记录类（T-ADMIN-029：typed IDs 一次性切换，零兼容层）
 * <p>
 * 用于更新通知公告的请求参数。标题和内容为必填项（全量更新语义）。
 * </p>
 *
 * @param id             公告ID（必填，用于定位公告）
 * @param title          公告标题（必填）
 * @param content        公告内容（必填，富文本）
 * @param noticeType     通知类型（可选，1=通知，2=公告）
 * @param targetType     目标类型（可选，ALL=全员，USER=指定用户；缺省 ALL；ORG 预留未实现拒绝）
 * @param targetUserIds  目标用户 ID 列表（targetType=USER 时必填非空；ALL 时必须为空）
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
     * 目标类型（ALL=全员，USER=指定用户）；缺省按 ALL 处理
     */
    @Pattern(regexp = "ALL|USER", message = "目标类型仅支持 ALL 或 USER")
    String targetType,

    /**
     * 目标用户 ID 列表（JSONB 数组落库；仅 targetType=USER 时允许非空）
     */
    List<@NotNull(message = "目标用户ID不能为null") Long> targetUserIds
) {}
