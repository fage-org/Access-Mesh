package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 用户通知关联实体类
 * <p>
 * 对应数据库表sys_user_notice，用于存储用户和通知的关联关系。
 * 记录用户是否已读某条通知及其读取时间。
 * </p>
 */
@Table("sys_user_notice")
public class SysUserNotice {

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
     * 通知ID
     */
    private Long noticeId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 读取时间
     */
    private LocalDateTime readAt;

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
     * 获取通知ID
     *
     * @return 通知ID
     */
    public Long getNoticeId() { return noticeId; }

    /**
     * 设置通知ID
     *
     * @param noticeId 通知ID
     */
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public Long getUserId() { return userId; }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public void setUserId(Long userId) { this.userId = userId; }

    /**
     * 获取是否已读
     *
     * @return 是否已读
     */
    public Boolean getIsRead() { return isRead; }

    /**
     * 设置是否已读
     *
     * @param isRead 是否已读
     */
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    /**
     * 获取读取时间
     *
     * @return 读取时间
     */
    public LocalDateTime getReadAt() { return readAt; }

    /**
     * 设置读取时间
     *
     * @param readAt 读取时间
     */
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}