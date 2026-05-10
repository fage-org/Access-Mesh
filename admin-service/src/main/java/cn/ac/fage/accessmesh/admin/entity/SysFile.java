package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统文件实体类
 * <p>
 * 对应数据库表sys_file，用于存储上传文件的信息。
 * 包括原始文件名、存储路径、文件大小、文件类型等。
 * </p>
 */
@Table("sys_file")
public class SysFile {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 存储文件名
     */
    private String fileName;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件访问URL
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型（MIME类型）
     */
    private String fileType;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除人ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，1=已删除）
     */
    private Long deleteFlag;

    /**
     * 获取主键ID
     *
     * @return 主键ID
     */
    public Long getId() { return id; }

    /**
     * 设置主键ID
     *
     * @param id 主键ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() { return tenantId; }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    /**
     * 获取原始文件名
     *
     * @return 原始文件名
     */
    public String getOriginalName() { return originalName; }

    /**
     * 设置原始文件名
     *
     * @param originalName 原始文件名
     */
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    /**
     * 获取存储文件名
     *
     * @return 存储文件名
     */
    public String getFileName() { return fileName; }

    /**
     * 设置存储文件名
     *
     * @param fileName 存储文件名
     */
    public void setFileName(String fileName) { this.fileName = fileName; }

    /**
     * 获取文件存储路径
     *
     * @return 文件存储路径
     */
    public String getFilePath() { return filePath; }

    /**
     * 设置文件存储路径
     *
     * @param filePath 文件存储路径
     */
    public void setFilePath(String filePath) { this.filePath = filePath; }

    /**
     * 获取文件访问URL
     *
     * @return 文件访问URL
     */
    public String getFileUrl() { return fileUrl; }

    /**
     * 设置文件访问URL
     *
     * @param fileUrl 文件访问URL
     */
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    /**
     * 获取文件大小
     *
     * @return 文件大小（字节）
     */
    public Long getFileSize() { return fileSize; }

    /**
     * 设置文件大小
     *
     * @param fileSize 文件大小（字节）
     */
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    /**
     * 获取文件类型
     *
     * @return 文件类型
     */
    public String getFileType() { return fileType; }

    /**
     * 设置文件类型
     *
     * @param fileType 文件类型
     */
    public void setFileType(String fileType) { this.fileType = fileType; }

    /**
     * 获取存储桶名称
     *
     * @return 存储桶名称
     */
    public String getBucketName() { return bucketName; }

    /**
     * 设置存储桶名称
     *
     * @param bucketName 存储桶名称
     */
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    /**
     * 获取创建人ID
     *
     * @return 创建人ID
     */
    public Long getCreatedBy() { return createdBy; }

    /**
     * 设置创建人ID
     *
     * @param createdBy 创建人ID
     */
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    /**
     * 获取更新人ID
     *
     * @return 更新人ID
     */
    public Long getUpdatedBy() { return updatedBy; }

    /**
     * 设置更新人ID
     *
     * @param updatedBy 更新人ID
     */
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    /**
     * 获取删除人ID
     *
     * @return 删除人ID
     */
    public Long getDeletedBy() { return deletedBy; }

    /**
     * 设置删除人ID
     *
     * @param deletedBy 删除人ID
     */
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * 设置更新时间
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 获取删除时间
     *
     * @return 删除时间
     */
    public LocalDateTime getDeletedAt() { return deletedAt; }

    /**
     * 设置删除时间
     *
     * @param deletedAt 删除时间
     */
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 获取删除标记
     *
     * @return 删除标记
     */
    public Long getDeleteFlag() { return deleteFlag; }

    /**
     * 设置删除标记
     *
     * @param deleteFlag 删除标记
     */
    public void setDeleteFlag(Long deleteFlag) { this.deleteFlag = deleteFlag; }
}