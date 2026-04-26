package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;

public record FileResp(
    Long id,
    String fileName,
    String originalName,
    String fileSuffix,
    String fileUrl,
    String fileSize,
    String fileType,
    String storagePath,
    LocalDateTime createdAt
) {}
