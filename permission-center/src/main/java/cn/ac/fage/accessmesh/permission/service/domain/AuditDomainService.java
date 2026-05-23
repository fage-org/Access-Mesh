package cn.ac.fage.accessmesh.permission.service.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计领域服务接口
 * <p>
 * 合并 PermissionChangeDomainService 和 OperationLogDomainService 两个旧接口。
 * 提供权限变更日志记录、操作日志记录、变更历史查询等审计功能。
 * </p>
 */
public interface AuditDomainService {

    // ===== 变更日志记录 =====

    /**
     * 批量记录权限变更日志
     *
     * @param context 变更上下文
     * @param changes 变更条目列表
     */
    void recordChangeLog(ChangeLogContext context, List<ChangeLogEntry> changes);

    // ===== 操作日志记录 =====

    /**
     * 异步记录操作日志
     *
     * @param module     操作所属模块名称
     * @param action     具体操作动作
     * @param targetType 操作目标类型
     * @param targetId   操作目标ID
     * @param summary    操作摘要描述
     * @param operatorId 操作者用户ID
     * @param ipAddress  操作者IP地址
     * @param requestId  请求唯一标识ID
     * @param tenantId   租户ID
     */
    void asyncRecordLog(String module, String action, String targetType, Long targetId,
                         String summary, Long operatorId, String ipAddress, String requestId, Long tenantId);

    // ===== 变更历史查询 =====

    /**
     * 查询近期变更历史
     *
     * @param tenantId   租户ID
     * @param userId     受影响的用户ID，可选
     * @param roleId     涉及的角色ID，可选
     * @param since      开始时间，可选
     * @param until      结束时间，可选
     * @param eventTypes 事件类型列表，可选
     * @param offset     分页偏移量
     * @param limit      每页条数
     * @return 变更日志列表
     */
    List<Object> queryRecentChanges(Long tenantId, Long userId, Long roleId,
                                    LocalDateTime since, LocalDateTime until,
                                    List<String> eventTypes, int offset, int limit);

    /**
     * 统计近期变更数量
     *
     * @param tenantId   租户ID
     * @param userId     受影响的用户ID，可选
     * @param roleId     涉及的角色ID，可选
     * @param since      开始时间，可选
     * @param until      结束时间，可选
     * @param eventTypes 事件类型列表，可选
     * @return 变更日志数量
     */
    long countRecentChanges(Long tenantId, Long userId, Long roleId,
                            LocalDateTime since, LocalDateTime until,
                            List<String> eventTypes);

    // ===== 内部记录类型 =====

    /**
     * 变更日志上下文记录类
     */
    record ChangeLogContext(
        Long tenantId,
        Long operatorId,
        String requestId,
        String changeSource,
        String changeReason
    ) {}

    /**
     * 变更日志条目记录类
     */
    record ChangeLogEntry(
        String entityType,
        Long entityId,
        String operation,
        String oldSnapshot,
        String newSnapshot,
        String diffSnapshot,
        Long[] affectedUserIds,
        Long[] affectedRoleIds
    ) {}
}
