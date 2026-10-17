package cn.ac.fage.accessmesh.access.audit.service.domain.impl;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
import cn.ac.fage.accessmesh.access.audit.entity.*;
import cn.ac.fage.accessmesh.access.audit.mapper.*;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditExportDomainService;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.stereotype.Service;
import java.util.List;
import static cn.ac.fage.accessmesh.access.infrastructure.util.StringUtils.normalizeFilterParam;
@Service
public class AuditExportDomainServiceImpl implements AuditExportDomainService {
    private final OperationLogMapper operations;
    private final PermissionChangeLogMapper changes;
    private final SysLoginLogMapper logins;
    public AuditExportDomainServiceImpl(OperationLogMapper operations, PermissionChangeLogMapper changes, SysLoginLogMapper logins) {
        this.operations = operations; this.changes = changes; this.logins = logins;
    }
    public Cursor<OperationLog> operations(Long tenantId, OperationLogExportReq r, int limit) {
        return operations.exportByCondition(tenantId, normalizeFilterParam(r.module()), normalizeFilterParam(r.action()),
            r.operatorId(), r.since(), r.until(), normalizeFilterParam(r.targetType()), limit, normalizeFilterParam(r.requestId()));
    }
    public Cursor<PermissionChangeLog> changes(Long tenantId, ChangeLogExportReq r, int limit) {
        String event = normalizeFilterParam(r.eventType());
        return changes.exportByCondition(tenantId, normalizeFilterParam(r.entityType()), r.entityId(), r.affectedUserId(),
            r.affectedRoleId(), r.since(), r.until(), event == null ? null : List.of(event), normalizeFilterParam(r.changeSource()), limit);
    }
    public Cursor<SysLoginLog> logins(Long tenantId, LoginLogExportReq r, int limit) {
        return logins.exportByCondition(tenantId, r.since(), r.until(), limit);
    }
}
