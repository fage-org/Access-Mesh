package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限变更日志领域服务实现类
 * <p>
 * 提供权限变更日志的记录功能。
 * 权限变更日志用于追踪权限系统的所有变更操作，
 * 包括角色分配、权限授予、配置修改等。
 * 每条日志记录变更前后的快照、差异、影响范围等信息，
 * 用于审计、追溯和同步。
 * 采用批量插入优化写入性能。
 * </p>
 */
@Service
public class PermissionChangeDomainServiceImpl implements PermissionChangeDomainService {

    private final PermissionChangeLogMapper changeLogMapper;

    /**
     * 构造函数注入依赖
     *
     * @param changeLogMapper 权限变更日志数据访问层
     */
    public PermissionChangeDomainServiceImpl(PermissionChangeLogMapper changeLogMapper) {
        this.changeLogMapper = changeLogMapper;
    }

    /**
     * 批量记录权限变更日志
     * <p>
     * 将多个变更操作批量记录到权限变更日志表。
     * 每条日志包含变更上下文（租户、操作者、来源）和变更详情
     * （实体类型、实体ID、操作类型、新旧快照、差异、影响范围）。
     * 使用批量插入优化，避免逐条插入的性能问题。
     * </p>
     *
     * @param context 变更上下文，包含租户ID、操作者ID、变更来源等公共信息
     * @param changes 变更条目列表，每个条目包含一个实体的变更详情
     */
    @Override
    public void record(ChangeLogContext context, List<ChangeLogEntry> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        List<PermissionChangeLog> logs = new ArrayList<>(changes.size());
        LocalDateTime now = LocalDateTime.now();

        for (ChangeLogEntry entry : changes) {
            PermissionChangeLog cl = new PermissionChangeLog();
            cl.setTenantId(context.tenantId());
            cl.setBizDomainId(context.bizDomainId());
            cl.setEntityType(entry.entityType());
            cl.setEntityId(entry.entityId());
            cl.setOperation(entry.operation());
            cl.setOldSnapshot(entry.oldSnapshot());
            cl.setNewSnapshot(entry.newSnapshot());
            cl.setDiffSnapshot(entry.diffSnapshot());
            cl.setAffectedAbstractUserIds(entry.affectedUserIds());
            cl.setAffectedAbstractRoleIds(entry.affectedRoleIds());
            cl.setChangeReason(context.changeReason());
            cl.setChangeSource(context.changeSource());
            cl.setRequestId(context.requestId());
            cl.setCreatedBy(context.operatorId());
            cl.setCreatedAt(now);
            logs.add(cl);
        }

        // 批量插入
        changeLogMapper.insertBatch(logs);
    }
}