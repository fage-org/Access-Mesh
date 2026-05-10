package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统通知公告实体类
 * <p>
 * 对应数据库表sys_notice，用于存储系统通知公告。
 * 支持不同类型的通知、目标用户配置、发布状态等。
 * </p>
 */
@Table("sys_notice")
public class SysNotice {

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
     * 通知类型
     */
    private String noticeType;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 目标类型（ALL=全员，USER=指定用户，ORG=指定组织）
     */
    private String targetType;

    /**
     * 目标ID列表（逗号分隔）
     */
    private String targetIds;

    /**
     * 状态（0=草稿，1=已发布）
     */
    private Integer status;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

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
     * 获取通知类型
     *
     * @return 通知类型
     */
    public String getNoticeType() { return noticeType; }

    /**
     * 设置通知类型
     *
     * @param noticeType 通知类型
     */
    public void setNoticeType(String noticeType) { this.noticeType = noticeType; }

    /**
     * 获取通知标题
     *
     * @return 通知标题
     */
    public String getTitle() { return title; }

    /**
     * 设置通知标题
     *
     * @param title 通知标题
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * 获取通知内容
     *
     * @return 通知内容
     */
    public String getContent() { return content; }

    /**
     * 设置通知内容
     *
     * @param content 通知内容
     */
    public void setContent(String content) { this.content = content; }

    /**
     * 获取目标类型
     *
     * @return 目标类型
     */
    public String getTargetType() { return targetType; }

    /**
     * 设置目标类型
     *
     * @param targetType 目标类型
     */
    public void setTargetType(String targetType) { this.targetType = targetType; }

    /**
     * 获取目标ID列表
     *
     * @return 目标ID列表
     */
    public String getTargetIds() { return targetIds; }

    /**
     * 设置目标ID列表
     *
     * @param targetIds 目标ID列表
     */
    public void setTargetIds(String targetIds) { this.targetIds = targetIds; }

    /**
     * 获取状态
     *
     * @return 状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置状态
     *
     * @param status 状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取发布时间
     *
     * @return 发布时间
     */
    public LocalDateTime getPublishedAt() { return publishedAt; }

    /**
     * 设置发布时间
     *
     * @param publishedAt 发布时间
     */
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

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