package org.dromara.permission.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 权限日志记录器
 *
 * 输出结构化 JSON 日志，便于日志平台解析和检索
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
public class PermissionLogWriter {

    private final ObjectMapper objectMapper;

    /**
     * 专用日志 Logger
     */
    private static final org.slf4j.Logger PERMISSION_LOG = org.slf4j.LoggerFactory.getLogger("PERMISSION_AUDIT");

    public PermissionLogWriter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 记录日志
     *
     * @param entry 日志条目
     */
    public void log(PermissionLogEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            PERMISSION_LOG.info(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize permission log entry: {}", e.getMessage());
        }
    }

    /**
     * 记录鉴权拒绝日志
     *
     * @param tenantId      租户ID
     * @param subjectKey    主体标识
     * @param resourceCode  资源编码
     * @param operationCode 操作编码
     * @param reason        拒绝原因
     */
    public void logDeny(String tenantId, String subjectKey, String resourceCode, String operationCode, String reason) {
        log(PermissionLogEntry.deny(tenantId, subjectKey, resourceCode, operationCode, reason));
    }

    /**
     * 记录快照刷新日志
     *
     * @param tenantId          租户ID
     * @param subjectKey        主体标识
     * @param permissionVersion 权限版本
     * @param durationMs        耗时（毫秒）
     */
    public void logSnapshotRefresh(String tenantId, String subjectKey, String permissionVersion, long durationMs) {
        log(PermissionLogEntry.snapshotRefresh(tenantId, subjectKey, permissionVersion, durationMs));
    }

    /**
     * 记录版本变更日志
     *
     * @param tenantId          租户ID
     * @param permissionVersion 权限版本
     * @param requestId         请求ID
     */
    public void logVersionChange(String tenantId, String permissionVersion, String requestId) {
        log(PermissionLogEntry.versionChange(tenantId, permissionVersion, requestId));
    }

    /**
     * 记录条件审核日志
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     * @param action      操作
     * @param reason      原因
     */
    public void logConditionAudit(String tenantId, Long conditionId, String action, String reason) {
        log(PermissionLogEntry.conditionAudit(tenantId, conditionId, action, reason));
    }

    /**
     * 记录冲突发现日志
     *
     * @param tenantId        租户ID
     * @param conflictRuleId  冲突规则ID
     * @param resourceCode    资源编码
     * @param firstOp         第一个操作
     * @param secondOp        第二个操作
     */
    public void logConflictFound(String tenantId, Long conflictRuleId, String resourceCode, String firstOp, String secondOp) {
        log(PermissionLogEntry.conflictFound(tenantId, conflictRuleId, resourceCode, firstOp, secondOp));
    }

    /**
     * 记录依赖断裂日志
     *
     * @param tenantId       租户ID
     * @param resourceCode   资源编码
     * @param operationCode  操作编码
     * @param gapDescription 缺口描述
     */
    public void logDependencyGap(String tenantId, String resourceCode, String operationCode, String gapDescription) {
        log(PermissionLogEntry.dependencyGap(tenantId, resourceCode, operationCode, gapDescription));
    }
}
