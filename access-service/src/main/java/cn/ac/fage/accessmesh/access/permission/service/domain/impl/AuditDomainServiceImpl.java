package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     * 使用@Async注解在独立有界线程池中执行，不阻塞主业务流程。
     * REQUIRES_NEW 开启独立短事务（T-ACCESS-007 §8.2 事务分级）：
     * 即使未来调用点处于外层事务，本日志写入也在独立事务提交，
     * 与主业务事务互不干扰。
     * 方法体不吞异常：写入失败时异常传播至 {@code AsyncUncaughtExceptionHandler}
     * （AsyncConfig 统一告警），调用方兜底（如 AuthServiceImpl.safeRecordLoginLog）
     * 在入口处隔离影响。
     * </p>
     */
    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asyncRecordLog(OperationLogEntry entry) {
        // 对齐 operation_log 列上限统一截断：本实现是所有直接 asyncRecordLog
        // 调用路径的统一落点（含 PermissionConflictDomainServiceImpl 冲突通知等绕过切面截断的
        // 内部动态日志），在此截断确保任何路径都不因列值超长触发插入失败丢失审计。
        // 入口级 @OperationLog 切面（OperationLogAspect）已在记录前截断，此处二次截断幂等兜底。
        OperationLog opLog = new OperationLog();
        opLog.setTenantId(entry.tenantId());
        opLog.setModule(entry.module());
        opLog.setAction(entry.action());
        opLog.setTargetType(entry.targetType());
        opLog.setTargetId(truncate(entry.targetId(), TARGET_ID_MAX_LEN));
        opLog.setSummary(truncate(entry.summary(), SUMMARY_MAX_LEN));
        opLog.setOperatorId(entry.operatorId());
        opLog.setOperatorName(truncate(entry.operatorName(), OPERATOR_NAME_MAX_LEN));
        opLog.setIpAddress(entry.ipAddress());
        opLog.setRequestId(entry.requestId());
        opLog.setRequestUrl(entry.requestUrl());
        // request_body 列随 T-ACCESS-025 参数序列化收敛停用（恒 NULL），不再写入
        opLog.setResponseCode(entry.responseCode());
        opLog.setCostTime(entry.costTime());
        opLog.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(opLog);
    }

    /** operation_log.target_id 列上限（VARCHAR(256)） */
    private static final int TARGET_ID_MAX_LEN = 256;
    /** operation_log.summary 列上限（VARCHAR(512)） */
    private static final int SUMMARY_MAX_LEN = 512;
    /** operation_log.operator_name 列上限（VARCHAR(256)） */
    private static final int OPERATOR_NAME_MAX_LEN = 256;

    /**
     * 按列上限截断字符串；null 或未超长原样返回。
     */
    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }


}
