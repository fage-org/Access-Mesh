package cn.ac.fage.accessmesh.access.audit.service;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
public interface AuditExportAppService {
    byte[] operations(OperationLogExportReq req);
    byte[] changes(ChangeLogExportReq req);
    byte[] logins(LoginLogExportReq req);
}
