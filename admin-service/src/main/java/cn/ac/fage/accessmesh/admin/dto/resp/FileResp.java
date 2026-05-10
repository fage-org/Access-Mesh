package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;

/**
 * 文件信息响应记录类
 * <p>
 * 用于返回文件上传后的信息。
 * 包含文件名、原始名称、后缀、URL、大小、类型、存储路径、创建时间。
 * </p>
 *
 * @param id           文件ID
 * @param fileName     文件名（存储名称）
 * @param originalName 原始文件名
 * @param fileSuffix   文件后缀
 * @param fileUrl      文件访问URL
 * @param fileSize     文件大小（格式化显示）
 * @param fileType     文件类型
 * @param storagePath  存储路径
 * @param createdAt    创建时间
 */
public record FileResp(
    /**
     * 文件ID
     */
    Long id,

    /**
     * 文件名（存储名称）
     */
    String fileName,

    /**
     * 原始文件名
     */
    String originalName,

    /**
     * 文件后缀
     */
    String fileSuffix,

    /**
     * 文件访问URL
     */
    String fileUrl,

    /**
     * 文件大小（格式化显示）
     */
    String fileSize,

    /**
     * 文件类型
     */
    String fileType,

    /**
     * 存储路径
     */
    String storagePath,

    /**
     * 创建时间
     */
    LocalDateTime createdAt
) {}