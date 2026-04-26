package cn.ac.fage.accessmesh.permission.service.domain;

public interface OperationLogDomainService {

    void asyncRecord(String module, String action, String targetType, Long targetId,
                     String summary, Long operatorId, String ipAddress, String requestId, Long tenantId);
}
