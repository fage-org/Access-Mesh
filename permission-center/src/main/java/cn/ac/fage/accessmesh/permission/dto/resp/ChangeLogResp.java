package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 变更日志响应体
 * <p>
 * 返回权限变更日志的详细信息，包括变更类型、前后快照、影响范围等。
 * 用于变更日志查询接口的响应，支持审计和溯源。
 * </p>
 *
 * @param id                   变更日志ID
 * @param tenantId             租户ID
 * @param bizDomainId          业务域ID
 * @param entityType           变更实体类型
 * @param entityId             变更实体ID
 * @param operation            变更操作类型
 * @param oldSnapshot          变更前快照JSON
 * @param newSnapshot          变更后快照JSON
 * @param diffSnapshot         变化差异快照JSON
 * @param affectedAbstractUserIds 受影响的用户ID数组
 * @param affectedAbstractRoleIds 受影响的角色ID数组
 * @param changeReason         变更原因
 * @param changeSource         变更来源编码
 * @param requestId            请求ID，用于关联追踪
 * @param createdAt            创建时间
 */
public record ChangeLogResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String entityType,
    Long entityId,
    String operation,
    String oldSnapshot,
    String newSnapshot,
    String diffSnapshot,
    Long[] affectedAbstractUserIds,
    Long[] affectedAbstractRoleIds,
    String changeReason,
    String changeSource,
    String requestId,
    LocalDateTime createdAt
) {}