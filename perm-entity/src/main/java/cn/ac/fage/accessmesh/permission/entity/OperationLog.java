package cn.ac.fage.accessmesh.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 * <p>
 * 表示用户操作行为的审计日志记录。
 * 记录操作的模块、动作、目标对象、操作者信息等。
 * 用于系统审计、安全分析和操作追溯。
 * </p>
 *
 * @author AccessMesh Team
 */
@Table("operation_log")
public class OperationLog {

    /**
     * 操作日志唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 操作模块，标识操作所属的功能模块
     */
    private String module;

    /**
     * 操作动作，标识具体的操作类型
     */
    private String action;

    /**
     * 目标类型，标识操作对象的类型
     */
    private String targetType;

    /**
     * 目标ID，标识操作对象的唯一标识
     */
    private Long targetId;

    /**
     * 操作摘要，简述操作内容
     */
    private String summary;

    /**
     * 操作者用户ID
     */
    private Long operatorId;

    /**
     * 操作者名称
     */
    private String operatorName;

    /**
     * IP地址，记录操作来源
     */
    private String ipAddress;

    /**
     * 请求ID，用于关联请求链路
     */
    private String requestId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 获取操作日志唯一标识
     *
     * @return 日志ID
     */
    public Long getId() { return id; }

    /**
     * 设置操作日志唯一标识
     *
     * @param id 日志ID
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
     * 获取操作动作
     *
     * @return 操作动作
     */
    public String getAction() { return action; }

    /**
     * 设置操作动作
     *
     * @param action 操作动作
     */
    public void setAction(String action) { this.action = action; }

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
     * 获取目标ID
     *
     * @return 目标ID
     */
    public Long getTargetId() { return targetId; }

    /**
     * 设置目标ID
     *
     * @param targetId 目标ID
     */
    public void setTargetId(Long targetId) { this.targetId = targetId; }

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
     * 获取操作者用户ID
     *
     * @return 操作者用户ID
     */
    public Long getOperatorId() { return operatorId; }

    /**
     * 设置操作者用户ID
     *
     * @param operatorId 操作者用户ID
     */
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    /**
     * 获取操作者名称
     *
     * @return 操作者名称
     */
    public String getOperatorName() { return operatorName; }

    /**
     * 设置操作者名称
     *
     * @param operatorName 操作者名称
     */
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

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