package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 操作日志响应体
 * <p>
 * 返回操作日志的详细信息，包括模块、操作、目标、操作者等。
 * 用于操作日志查询接口的响应，支持审计和追踪。
 * </p>
 *
 * @param id           日志ID
 * @param tenantId     租户ID
 * @param module       操作模块
 * @param action       操作动作
 * @param targetType   目标类型
 * @param targetId     目标ID（T-ACCESS-002 起为字符串，兼容业务键与数值 ID）
 * @param summary      操作摘要
 * @param operatorId   操作者ID
 * @param operatorName 操作者名称
 * @param ipAddress    IP地址
 * @param requestId    请求ID，用于关联追踪
 * @param createdAt    创建时间
 */
public record OperationLogResp(
    Long id,
    Long tenantId,
    String module,
    String action,
    String targetType,
    String targetId,
    String summary,
    Long operatorId,
    String operatorName,
    String ipAddress,
    String requestId,
    LocalDateTime createdAt
) {}