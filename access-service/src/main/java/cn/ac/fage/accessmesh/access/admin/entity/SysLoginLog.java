package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统登录日志实体类
 * <p>
 * 对应数据库表sys_login_log，用于记录用户登录日志。
 * 包括登录方式、客户端ID、IP地址、用户代理、登录状态等。
 * </p>
 */
@Getter
@Setter
@Table("sys_login_log")
public class SysLoginLog {

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
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 登录类型
     */
    private String loginType;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理（浏览器信息）
     */
    private String userAgent;

    /**
     * 登录地点
     */
    private String location;

    /**
     * 登录状态（0=失败，1=成功）
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 登录时间
     */
    private LocalDateTime loginAt;
}