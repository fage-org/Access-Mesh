package cn.ac.fage.accessmesh.access.permission.service.domain;

import java.util.List;

/**
 * 审计领域服务接口
 * <p>
 * 合并 PermissionChangeDomainService 和 OperationLogDomainService 两个旧接口。
 * 提供权限变更日志记录、操作日志记录等审计功能（变更历史查询已随 T-PERM-059 排查端点删除，2026-09-10）。
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
     * <p>
     * 入口级操作日志由 {@code @OperationLog} AOP 构造 {@link OperationLogEntry} 后调用；
     * 内部动态日志（冲突通知等非入口场景）亦通过本方法记录。
     * 实现使用 {@code @Async} 有界线程池异步写入，并以 {@code REQUIRES_NEW} 开启独立短事务
     * （T-ACCESS-007 §8.2 事务分级），写入失败仅告警、不影响主业务事务。
     * </p>
     *
     * @param entry 操作日志条目（含 HTTP 上下文；request_body 已随 T-ACCESS-025 停用不入本记录）
     */
    void asyncRecordLog(OperationLogEntry entry);



    // ===== 内部记录类型 =====

    /**
     * 操作日志条目（T-ACCESS-007 参数对象化）。
     * <p>
     * 承载 operation_log 表全部业务列（id/createdAt 由实现填充；request_body 列
     * 随 T-ACCESS-025 参数序列化收敛停用，恒为 NULL，不入本记录）。
     * 由 {@code @OperationLog} AOP 在同步线程构造：采集 HTTP 上下文
     * （requestUrl/ipAddress），记录响应码与耗时；operatorName 从登录会话读取，
     * 未登录/无会话调用为 null。内部动态日志（冲突通知等）除 tenantId/module/action/
     * summary 外其余字段为 null。
     * </p>
     */
    record OperationLogEntry(
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
        String requestUrl,
        Integer responseCode,
        Integer costTime
    ) {}

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
