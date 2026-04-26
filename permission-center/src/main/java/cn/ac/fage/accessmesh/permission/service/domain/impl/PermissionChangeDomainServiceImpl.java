package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PermissionChangeDomainServiceImpl implements PermissionChangeDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionChangeDomainServiceImpl.class);

    private final PermissionChangeLogMapper changeLogMapper;

    public PermissionChangeDomainServiceImpl(PermissionChangeLogMapper changeLogMapper) {
        this.changeLogMapper = changeLogMapper;
    }

    @Override
    @Async
    public void record(ChangeLogContext context, List<ChangeLogEntry> changes) {
        for (ChangeLogEntry entry : changes) {
            try {
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
                cl.setCreatedAt(LocalDateTime.now());
                changeLogMapper.insert(cl);
            } catch (Exception e) {
                log.error("Failed to record permission change log", e);
            }
        }
    }
}
