package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

public record NoticeCreateReq(
    @NotBlank(message = "公告标题不能为空")
    String title,
    String content,
    Integer noticeType,
    String targetUserIds
) {}
