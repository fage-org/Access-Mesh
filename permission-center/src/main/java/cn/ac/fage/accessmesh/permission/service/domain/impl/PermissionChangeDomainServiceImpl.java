package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PermissionChangeDomainServiceImpl implements PermissionChangeDomainService {

    private final PermissionChangeLogMapper changeLogMapper;

    public PermissionChangeDomainServiceImpl(PermissionChangeLogMapper changeLogMapper) {
        this.changeLogMapper = changeLogMapper;
    }

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
