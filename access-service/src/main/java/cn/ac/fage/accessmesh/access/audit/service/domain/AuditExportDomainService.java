package cn.ac.fage.accessmesh.access.audit.service.domain;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
import cn.ac.fage.accessmesh.access.audit.entity.*;
import org.apache.ibatis.cursor.Cursor;
public interface AuditExportDomainService {
    Cursor<OperationLog> operations(Long tenantId, OperationLogExportReq req, int limit);
    Cursor<PermissionChangeLog> changes(Long tenantId, ChangeLogExportReq req, int limit);
    Cursor<SysLoginLog> logins(Long tenantId, LoginLogExportReq req, int limit);
}
