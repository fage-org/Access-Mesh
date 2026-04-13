package org.dromara.permission.log;

import lombok.Data;

import java.time.Instant;

/**
 * 权限日志条目
 *
 * @author RuoYi-Cloud-Plus
 */
@Data
public class PermissionLogEntry {

    /**
     * 时间戳
     */
    private Instant timestamp;

    /**
     * 事件类型
     */
    private PermissionEventType eventType;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 主体标识
     */
    private String subjectKey;

    /**
     * 权限版本
     */
    private String permissionVersion;

    /**
     * 资源编码
     */
    private String resourceCode;

    /**
     * 操作编码
     */
    private String operationCode;

    /**
     * 原因
     */
    private String reason;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 额外信息
     */
    private String extra;

    /**
     * 事件类型枚举
     */
    public enum PermissionEventType {
        /**
         * 鉴权拒绝
         */
        DENY,

        /**
         * 快照刷新
         */
        SNAPSHOT_REFRESH,

        /**
         * 版本变更
         */
        VERSION_CHANGE,

        /**
         * 条件审核
         */
        CONDITION_AUDIT,

        /**
         * 冲突发现
         */
        CONFLICT_FOUND,

        /**
         * 依赖断裂
         */
        DEPENDENCY_GAP
    }

    /**
     * 创建鉴权拒绝日志
     */
    public static PermissionLogEntry deny(String tenantId, String subjectKey, String resourceCode, String operationCode, String reason) {
        PermissionLogEntry entry = new PermissionLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setEventType(PermissionEventType.DENY);
        entry.setTenantId(tenantId);
        entry.setSubjectKey(subjectKey);
        entry.setResourceCode(resourceCode);
        entry.setOperationCode(operationCode);
        entry.setReason(reason);
        return entry;
    }

    /**
     * 创建快照刷新日志
     */
    public static PermissionLogEntry snapshotRefresh(String tenantId, String subjectKey, String permissionVersion, long durationMs) {
        PermissionLogEntry entry = new PermissionLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setEventType(PermissionEventType.SNAPSHOT_REFRESH);
        entry.setTenantId(tenantId);
        entry.setSubjectKey(subjectKey);
        entry.setPermissionVersion(permissionVersion);
        entry.setDurationMs(durationMs);
        return entry;
    }

    /**
     * 创建版本变更日志
     */
    public static PermissionLogEntry versionChange(String tenantId, String permissionVersion, String requestId) {
        PermissionLogEntry entry = new PermissionLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setEventType(PermissionEventType.VERSION_CHANGE);
        entry.setTenantId(tenantId);
        entry.setPermissionVersion(permissionVersion);
        entry.setRequestId(requestId);
        return entry;
    }

    /**
     * 创建条件审核日志
     */
    public static PermissionLogEntry conditionAudit(String tenantId, Long conditionId, String action, String reason) {
        PermissionLogEntry entry = new PermissionLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setEventType(PermissionEventType.CONDITION_AUDIT);
        entry.setTenantId(tenantId);
        entry.setExtra(String.format("conditionId=%d, action=%s", conditionId, action));
        entry.setReason(reason);
        return entry;
    }

    /**
     * 创建冲突发现日志
     */
    public static PermissionLogEntry conflictFound(String tenantId, Long conflictRuleId, String resourceCode, String firstOp, String secondOp) {
        PermissionLogEntry entry = new PermissionLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setEventType(PermissionEventType.CONFLICT_FOUND);
        entry.setTenantId(tenantId);
        entry.setResourceCode(resourceCode);
        entry.setExtra(String.format("conflictRuleId=%d, firstOp=%s, secondOp=%s", conflictRuleId, firstOp, secondOp));
        return entry;
    }

    /**
     * 创建依赖断裂日志
     */
    public static PermissionLogEntry dependencyGap(String tenantId, String resourceCode, String operationCode, String gapDescription) {
        PermissionLogEntry entry = new PermissionLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setEventType(PermissionEventType.DEPENDENCY_GAP);
        entry.setTenantId(tenantId);
        entry.setResourceCode(resourceCode);
        entry.setOperationCode(operationCode);
        entry.setReason(gapDescription);
        return entry;
    }
}
