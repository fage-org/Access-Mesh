package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 系统审计日志实体类
 * <p>
 * 对应数据库表sys_audit_log，用于记录用户的操作审计日志。
 * 包括操作模块、操作类型、请求信息、响应信息等。
 * </p>
 */
@Table("sys_audit_log")
public class SysAuditLog {

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
     * 操作用户ID
     */
    private Long userId;

    /**
     * 操作用户名
     */
    private String username;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作类型
     */
    private String action;

    /**
     * 目标资源类型
     */
    private String targetType;

    /**
     * 目标资源ID
     */
    private String targetId;

    /**
     * 操作摘要
     */
    private String summary;

    /**
     * 操作IP地址
     */
    private String ipAddress;

    /**
     * 请求ID（用于追踪）
     */
    private String requestId;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求体内容
     */
    private String requestBody;

    /**
     * 响应状态码
     */
    private Integer responseCode;

    /**
     * 操作耗时（毫秒）
     */
    private Integer costTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

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
     * 获取操作用户ID
     *
     * @return 操作用户ID
     */
    public Long getUserId() { return userId; }

    /**
     * 设置操作用户ID
     *
     * @param userId 操作用户ID
     */
    public void setUserId(Long userId) { this.userId = userId; }

    /**
     * 获取操作用户名
     *
     * @return 操作用户名
     */
    public String getUsername() { return username; }

    /**
     * 设置操作用户名
     *
     * @param username 操作用户名
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * 获取操作模块
     *
     * @return 操作模块
     */
    public String getModule() { return module; }

    /**
     * 设置操作模块
     *
     * @param module 操作模块
     */
    public void setModule(String module) { this.module = module; }

    /**
     * 获取操作类型
     *
     * @return 操作类型
     */
    public String getAction() { return action; }

    /**
     * 设置操作类型
     *
     * @param action 操作类型
     */
    public void setAction(String action) { this.action = action; }

    /**
     * 获取目标资源类型
     *
     * @return 目标资源类型
     */
    public String getTargetType() { return targetType; }

    /**
     * 设置目标资源类型
     *
     * @param targetType 目标资源类型
     */
    public void setTargetType(String targetType) { this.targetType = targetType; }

    /**
     * 获取目标资源ID
     *
     * @return 目标资源ID
     */
    public String getTargetId() { return targetId; }

    /**
     * 设置目标资源ID
     *
     * @param targetId 目标资源ID
     */
    public void setTargetId(String targetId) { this.targetId = targetId; }

    /**
     * 获取操作摘要
     *
     * @return 操作摘要
     */
    public String getSummary() { return summary; }

    /**
     * 设置操作摘要
     *
     * @param summary 操作摘要
     */
    public void setSummary(String summary) { this.summary = summary; }

    /**
     * 获取操作IP地址
     *
     * @return 操作IP地址
     */
    public String getIpAddress() { return ipAddress; }

    /**
     * 设置操作IP地址
     *
     * @param ipAddress 操作IP地址
     */
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    /**
     * 获取请求ID
     *
     * @return 请求ID
     */
    public String getRequestId() { return requestId; }

    /**
     * 设置请求ID
     *
     * @param requestId 请求ID
     */
    public void setRequestId(String requestId) { this.requestId = requestId; }

    /**
     * 获取请求URL
     *
     * @return 请求URL
     */
    public String getRequestUrl() { return requestUrl; }

    /**
     * 设置请求URL
     *
     * @param requestUrl 请求URL
     */
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    /**
     * 获取请求体内容
     *
     * @return 请求体内容
     */
    public String getRequestBody() { return requestBody; }

    /**
     * 设置请求体内容
     *
     * @param requestBody 请求体内容
     */
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }

    /**
     * 获取响应状态码
     *
     * @return 响应状态码
     */
    public Integer getResponseCode() { return responseCode; }

    /**
     * 设置响应状态码
     *
     * @param responseCode 响应状态码
     */
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }

    /**
     * 获取操作耗时
     *
     * @return 操作耗时（毫秒）
     */
    public Integer getCostTime() { return costTime; }

    /**
     * 设置操作耗时
     *
     * @param costTime 操作耗时（毫秒）
     */
    public void setCostTime(Integer costTime) { this.costTime = costTime; }

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
}