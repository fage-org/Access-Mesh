package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.audit.config.AuditLogSettings;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
import cn.ac.fage.accessmesh.access.audit.dto.resp.*;
import cn.ac.fage.accessmesh.access.audit.service.AuditExportAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditExportDomainService;
import cn.ac.fage.accessmesh.access.audit.util.AuditCsvWriter;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

@Service
// One cursor SELECT holds its statement snapshot, even while cleanup commits in another transaction.
@Transactional(readOnly = true, timeout = 60)
public class AuditExportAppServiceImpl implements AuditExportAppService {
    private final AuditExportDomainService domain;
    private final QueryGate gate;
    private final TypeResolutionService types;
    private final AuditLogSettings settings;
    public AuditExportAppServiceImpl(AuditExportDomainService domain, QueryGate gate, TypeResolutionService types, AuditLogSettings settings) {
        this.domain = domain; this.gate = gate; this.types = types; this.settings = settings;
    }
    private Long authorize(String type, boolean login) {
        Long tenant = TenantContextHolder.getTenantId();
        Long subject = OperatorContext.getOperatorId();
        if (login && subject != null) subject = types.resolveUserId(tenant, LocalProjectionOwner.SUBJECT_LOCAL_USER, subject.toString());
        if (!gate.hasPermissionByCode(tenant, subject, type, null, OperationCode.VIEW)) throw new SecurityException("Permission denied: VIEW on " + type);
        return tenant;
    }
    private AuditCsvWriter writer() { return new AuditCsvWriter(settings.maxRows(), settings.maxBytes()); }
    @Override
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module = "ACCESS", action = "AUDIT_EXPORT", targetType = "operation_log", targetId = "", summary = "'export audit logs'")
    public byte[] operations(OperationLogExportReq req) {
        Long tenant = authorize(ResourceTypeCode.OPERATION_LOG, false);
        var csv = writer();
        csv.header("id", "tenantId", "module", "action", "targetType", "targetId", "summary", "operatorId", "operatorName", "ipAddress", "requestId", "createdAt");
        try (var cursor = domain.operations(tenant, req, settings.maxRows() + 1)) {
            for (var entity : cursor) {
                var r = OperationLogResp.from(entity);
                csv.row(r.id(), r.tenantId(), r.module(), r.action(), r.targetType(), r.targetId(), r.summary(), r.operatorId(), r.operatorName(), r.ipAddress(), r.requestId(), r.createdAt());
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return csv.bytes();
    }
    @Override
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module = "ACCESS", action = "AUDIT_EXPORT", targetType = "permission_change_log", targetId = "", summary = "'export audit logs'")
    public byte[] changes(ChangeLogExportReq req) {
        Long tenant = authorize(ResourceTypeCode.PERMISSION_CHANGE_LOG, false);
        var csv = writer();
        csv.header("id", "tenantId", "entityType", "entityId", "operation", "oldSnapshot", "newSnapshot", "diffSnapshot", "affectedAbstractUserIds", "affectedAbstractRoleIds", "changeReason", "changeSource", "createdBy", "requestId", "createdAt");
        try (var cursor = domain.changes(tenant, req, settings.maxRows() + 1)) {
            for (var entity : cursor) {
                var r = ChangeLogResp.from(entity);
                csv.row(r.id(), r.tenantId(), r.entityType(), r.entityId(), r.operation(), r.oldSnapshot(), r.newSnapshot(), r.diffSnapshot(),
                    r.affectedAbstractUserIds() == null ? null : Arrays.toString(r.affectedAbstractUserIds()),
                    r.affectedAbstractRoleIds() == null ? null : Arrays.toString(r.affectedAbstractRoleIds()), r.changeReason(), r.changeSource(), r.createdBy(), r.requestId(), r.createdAt());
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return csv.bytes();
    }
    @Override
    @cn.ac.fage.accessmesh.access.audit.aop.OperationLog(module = "ACCESS", action = "AUDIT_EXPORT", targetType = "sys_login_log", targetId = "", summary = "'export audit logs'")
    public byte[] logins(LoginLogExportReq req) {
        Long tenant = authorize(ResourceTypeCode.OPERATION_LOG, true);
        var csv = writer();
        csv.header("id", "username", "clientId", "loginType", "status", "ipAddress", "userAgent", "failReason", "loginAt");
        try (var cursor = domain.logins(tenant, req, settings.maxRows() + 1)) {
            for (var entity : cursor) {
                var r = LoginLogResp.from(entity);
                csv.row(r.id(), r.username(), r.clientId(), r.loginType(), r.status(), r.ipAddress(), r.userAgent(), r.failReason(), r.loginAt());
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return csv.bytes();
    }
}
