package cn.ac.fage.accessmesh.access.admin.dto.resp;

import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;

import java.time.LocalDateTime;

/**
 * 审计日志响应记录类
 * <p>
 * 用于返回审计日志查询结果。
 * 排除敏感字段（如requestBody，可能包含密码、令牌或PII）。
 * </p>
 *
 * @param id          审计日志ID
 * @param module      操作模块
 * @param operation   操作类型
 * @param targetType  目标类型
 * @param targetId    目标ID
 * @param summary     操作摘要
 * @param username    操作用户名
 * @param uri         请求URI
 * @param ipAddress   IP地址
 * @param statusCode  HTTP状态码
 * @param durationMs  执行耗时（毫秒）
 * @param createdAt   创建时间
 */
public record AuditLogResp(
    /**
     * 审计日志ID
     */
    Long id,

    /**
     * 操作模块
     */
    String module,

    /**
     * 操作类型
     */
    String operation,

    /**
     * 目标类型
     */
    String targetType,

    /**
     * 目标ID
     */
    String targetId,

    /**
     * 操作摘要
     */
    String summary,

    /**
     * 操作用户名
     */
    String username,

    /**
     * 请求URI
     */
    String uri,

    /**
     * IP地址
     */
    String ipAddress,

    /**
     * HTTP状态码
     */
    Integer statusCode,

    /**
     * 执行耗时（毫秒）
     */
    Integer durationMs,

    /**
     * 创建时间
     */
    LocalDateTime createdAt
) {
    /**
     * 从实体转换为响应DTO（排除requestBody等敏感字段）
     *
     * @param entity 审计日志实体
     * @return 审计日志响应DTO，entity为null时返回null
     */
    public static AuditLogResp from(OperationLog entity) {
        return new AuditLogResp(
            entity.getId(),
            entity.getModule(),
            entity.getAction(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getSummary(),
            entity.getUsername(),
            entity.getRequestUrl(),
            entity.getIpAddress(),
            entity.getResponseCode(),
            entity.getCostTime(),
            entity.getCreatedAt()
        );
    }
}