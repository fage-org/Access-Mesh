package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;

public record NoticeResp(
    Long id,
    String title,
    String content,
    Integer noticeType,
    String targetUserIds,
    Integer status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
