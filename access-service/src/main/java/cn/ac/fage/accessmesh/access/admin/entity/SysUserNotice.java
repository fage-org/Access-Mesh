package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户通知关联实体类
 * <p>
 * 对应数据库表sys_user_notice，用于存储用户和通知的关联关系。
 * 记录用户是否已读某条通知及其读取时间。
 * </p>
 */
@Getter
@Setter
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
}