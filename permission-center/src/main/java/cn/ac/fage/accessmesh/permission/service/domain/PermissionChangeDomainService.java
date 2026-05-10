package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.List;

/**
 * 权限变更领域服务接口
 * <p>
 * 提供权限变更日志的记录功能。
 * 权限变更日志用于追踪权限系统的所有变更操作，
 * 包括角色分配、权限授予、配置修改等。
 * 每条日志记录变更前后的快照、差异、影响范围等信息，
 * 用于审计、追溯和同步。
 * </p>
 */
public interface PermissionChangeDomainService {

    /**
     * 批量记录权限变更日志
     * <p>
     * 将多个变更操作批量记录到权限变更日志表。
     * 每条日志包含变更上下文和变更详情。
     * </p>
     *
     * @param context 变更上下文，包含租户ID、操作者ID、变更来源等公共信息
     * @param changes 变更条目列表，每个条目包含一个实体的变更详情
     */
    void record(ChangeLogContext context, List<ChangeLogEntry> changes);

    /**
     * 变更日志上下文记录类
     * <p>
     * 包含变更操作的公共信息，适用于同一批次的所有变更条目。
     * </p>
     */
    record ChangeLogContext(
        Long tenantId,       // 租户ID
        Long bizDomainId,    // 业务域ID
        Long operatorId,     // 操作者ID
        String requestId,    // 请求ID，用于关联同一请求的多个变更
        String changeSource, // 变更来源（如MANUAL、SYNC、IMPORT等）
        String changeReason  // 变更原因描述
    ) {}

    /**
     * 变更日志条目记录类
     * <p>
     * 包含单个实体的变更详情，包括变更类型、实体标识、
     * 变更前后快照、差异快照、影响的用户和角色列表。
     * </p>
     */
    record ChangeLogEntry(
        String entityType,     // 实体类型（如abstract_role、role_resource_permission等）
        Long entityId,         // 实体ID
        String operation,      // 操作类型（如CREATE、UPDATE、DELETE等）
        String oldSnapshot,    // 变更前快照JSON
        String newSnapshot,    // 变更后快照JSON
        String diffSnapshot,   // 差异快照JSON
        Long[] affectedUserIds, // 影响的用户ID数组
        Long[] affectedRoleIds  // 影响的角色ID数组
    ) {}
}