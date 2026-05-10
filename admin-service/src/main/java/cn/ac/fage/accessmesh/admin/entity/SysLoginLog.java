package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统登录日志实体类
 * <p>
 * 对应数据库表sys_login_log，用于记录用户登录日志。
 * 包括登录方式、客户端ID、IP地址、用户代理、登录状态等。
 * </p>
 */
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
     * 登录状态（0=成功，1=失败）
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
     * 获取用户名
     *
     * @return 用户名
     */
    public String getUsername() { return username; }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * 获取登录类型
     *
     * @return 登录类型
     */
    public String getLoginType() { return loginType; }

    /**
     * 设置登录类型
     *
     * @param loginType 登录类型
     */
    public void setLoginType(String loginType) { this.loginType = loginType; }

    /**
     * 获取客户端ID
     *
     * @return 客户端ID
     */
    public String getClientId() { return clientId; }

    /**
     * 设置客户端ID
     *
     * @param clientId 客户端ID
     */
    public void setClientId(String clientId) { this.clientId = clientId; }

    /**
     * 获取IP地址
     *
     * @return IP地址
     */
    public String getIpAddress() { return ipAddress; }

    /**
     * 设置IP地址
     *
     * @param ipAddress IP地址
     */
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    /**
     * 获取用户代理
     *
     * @return 用户代理
     */
    public String getUserAgent() { return userAgent; }

    /**
     * 设置用户代理
     *
     * @param userAgent 用户代理
     */
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    /**
     * 获取登录地点
     *
     * @return 登录地点
     */
    public String getLocation() { return location; }

    /**
     * 设置登录地点
     *
     * @param location 登录地点
     */
    public void setLocation(String location) { this.location = location; }

    /**
     * 获取登录状态
     *
     * @return 登录状态
     */
    public Integer getStatus() { return status; }

    /**
     * 设置登录状态
     *
     * @param status 登录状态
     */
    public void setStatus(Integer status) { this.status = status; }

    /**
     * 获取失败原因
     *
     * @return 失败原因
     */
    public String getFailReason() { return failReason; }

    /**
     * 设置失败原因
     *
     * @param failReason 失败原因
     */
    public void setFailReason(String failReason) { this.failReason = failReason; }

    /**
     * 获取登录时间
     *
     * @return 登录时间
     */
    public LocalDateTime getLoginAt() { return loginAt; }

    /**
     * 设置登录时间
     *
     * @param loginAt 登录时间
     */
    public void setLoginAt(LocalDateTime loginAt) { this.loginAt = loginAt; }
}