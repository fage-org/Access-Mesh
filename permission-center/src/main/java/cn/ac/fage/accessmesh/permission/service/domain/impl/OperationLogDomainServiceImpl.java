package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OperationLogDomainServiceImpl implements OperationLogDomainService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogDomainServiceImpl.class);

    private final OperationLogMapper operationLogMapper;

    public OperationLogDomainServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Async
    public void asyncRecord(String module, String action, String targetType, Long targetId,
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
}
