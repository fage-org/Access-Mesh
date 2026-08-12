package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计领域服务实现类
 * <p>
 * 合并 PermissionChangeDomainServiceImpl 和 OperationLogDomainServiceImpl 两个旧实现。
 * 提供权限变更日志记录、操作日志异步记录、变更历史查询等审计功能。
 * 操作日志使用@Async注解异步写入，不阻塞主业务流程。
 * 变更日志使用批量插入优化写入性能。
 * </p>
 */
@Service
public class AuditDomainServiceImpl implements AuditDomainService {

    private static final Logger log = LoggerFactory.getLogger(AuditDomainServiceImpl.class);

    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;

    /**
     * 构造函数注入依赖
     *
     * @param changeLogMapper      权限变更日志数据访问层
     * @param operationLogMapper   操作日志数据访问层
     */
    public AuditDomainServiceImpl(PermissionChangeLogMapper changeLogMapper,
                                   OperationLogMapper operationLogMapper) {
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
    }

    // ===== 变更日志记录 =====

    /**
     * 批量记录权限变更日志
     * <p>
     * 将多个变更操作批量记录到权限变更日志表。
     * 使用批量插入优化，避免逐条插入的性能问题。
     * </p>
     *
     * @param context 变更上下文
     * @param changes 变更条目列表
     */
    @Override
    public void recordChangeLog(ChangeLogContext context, List<ChangeLogEntry> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        List<PermissionChangeLog> logs = new ArrayList<>(changes.size());
        LocalDateTime now = LocalDateTime.now();

        for (ChangeLogEntry entry : changes) {
            PermissionChangeLog cl = new PermissionChangeLog();
            cl.setTenantId(context.tenantId());
            cl.setEntityType(entry.entityType());
            cl.setEntityId(entry.entityId());
            cl.setOperation(entry.operation());
            cl.setOldSnapshot(entry.oldSnapshot());
            cl.setNewSnapshot(entry.newSnapshot());
            cl.setDiffSnapshot(entry.diffSnapshot());
            if (entry.oldSnapshot() != null) JsonValidationUtils.validateJson(entry.oldSnapshot());
            if (entry.newSnapshot() != null) JsonValidationUtils.validateJson(entry.newSnapshot());
            if (entry.diffSnapshot() != null) JsonValidationUtils.validateJson(entry.diffSnapshot());
            cl.setAffectedAbstractUserIds(entry.affectedUserIds());
            cl.setAffectedAbstractRoleIds(entry.affectedRoleIds());
            cl.setChangeReason(context.changeReason());
            cl.setChangeSource(context.changeSource());
            cl.setRequestId(context.requestId());
            cl.setCreatedBy(context.operatorId());
            cl.setCreatedAt(now);
            logs.add(cl);
        }

        changeLogMapper.insertBatch(logs);
    }

    // ===== 操作日志记录 =====

    /**
     * 异步记录操作日志
     * <p>
     * 使用@Async注解在独立线程池中执行，不阻塞主业务流程。
     * 记录失败时仅打印错误日志，不影响业务操作结果。
     * </p>
     */
    @Override
    @Async
    public void asyncRecordLog(String module, String action, String targetType, Long targetId,
                                String summary, Long operatorId, String ipAddress, String requestId, Long tenantId) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setTenantId(tenantId);
            opLog.setModule(module);
            opLog.setAction(action);
            opLog.setTargetType(targetType);
            opLog.setTargetId(targetId);
            opLog.setSummary(summary);
            opLog.setOperatorId(operatorId);
            opLog.setIpAddress(ipAddress);
            opLog.setRequestId(requestId);
            opLog.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(opLog);
        } catch (Exception e) {
            log.error("Failed to record operation log", e);
        }
    }

    // ===== 变更历史查询 =====

    /**
     * 查询近期变更历史
     */
    @Override
    public List<PermissionChangeLog> queryRecentChanges(Long tenantId, Long userId, Long roleId,
                                                          LocalDateTime since, LocalDateTime until,
                                                          List<String> eventTypes, int offset, int limit) {
        return changeLogMapper.selectFiltered(tenantId, userId, roleId, since, until, eventTypes, offset, limit);
    }

    /**
     * 统计近期变更数量
     */
    @Override
    public long countRecentChanges(Long tenantId, Long userId, Long roleId,
                                    LocalDateTime since, LocalDateTime until,
                                    List<String> eventTypes) {
        return changeLogMapper.countFiltered(tenantId, userId, roleId, since, until, eventTypes);
    }
}
