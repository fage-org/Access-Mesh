package cn.ac.fage.accessmesh.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统审计日志实体类
 * <p>
 * 对应数据库表sys_audit_log，用于记录用户的操作审计日志。
 * 包括操作模块、操作类型、请求信息、响应信息等。
 * </p>
 */
@Getter
@Setter
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
}