package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志领域服务实现类
 * <p>
 * 实现操作日志的异步记录功能，通过Spring的@Async注解实现非阻塞写入
 * </p>
 */
@Service
public class OperationLogDomainServiceImpl implements OperationLogDomainService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogDomainServiceImpl.class);

    private final OperationLogMapper operationLogMapper;

    /**
     * 构造函数注入操作日志Mapper
     *
     * @param operationLogMapper 操作日志数据访问层
     */
    public OperationLogDomainServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 异步记录操作日志
     * <p>
     * 使用@Async注解在独立线程池中执行，不阻塞主业务流程。
     * 记录失败时仅打印错误日志，不影响业务操作结果。
     * </p>
     *
     * @param module     操作所属模块名称
     * @param action     具体操作动作
     * @param targetType 操作目标类型
     * @param targetId   操作目标ID
     * @param summary    操作摘要描述
     * @param operatorId 操作者用户ID
     * @param ipAddress  操作者IP地址
     * @param requestId  请求唯一标识ID
     * @param tenantId   租户ID
     */
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
