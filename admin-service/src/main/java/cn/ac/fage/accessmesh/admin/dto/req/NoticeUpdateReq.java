package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoticeUpdateReq(
    @NotNull(message = "公告ID不能为空")
    Long id,
    
    @NotBlank(message = "公告标题不能为空")
    String title,
    
    @NotBlank(message = "公告内容不能为空")
    String content,
    
    Integer noticeType,
    
    String targetUserIds  // 可选，目标用户ID列表
) {}
