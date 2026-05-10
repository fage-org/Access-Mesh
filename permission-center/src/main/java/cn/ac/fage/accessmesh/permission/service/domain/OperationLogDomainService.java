package cn.ac.fage.accessmesh.permission.service.domain;

/**
 * 操作日志领域服务接口
 * <p>
 * 提供操作日志的异步记录功能，用于追踪系统中的各类操作行为
 * </p>
 */
public interface OperationLogDomainService {

    /**
     * 异步记录操作日志
     * <p>
     * 将操作日志异步写入数据库，不阻塞主业务流程
     * </p>
     *
     * @param module     操作所属模块名称，如"角色管理"、"资源管理"
     * @param action     具体操作动作，如"创建"、"删除"、"修改"
     * @param targetType 操作目标类型，如"ROLE"、"RESOURCE"
     * @param targetId   操作目标ID
     * @param summary    操作摘要描述
     * @param operatorId 操作者用户ID
     * @param ipAddress  操作者IP地址
     * @param requestId  请求唯一标识ID
     * @param tenantId   租户ID
     */
    void asyncRecord(String module, String action, String targetType, Long targetId,
                     String summary, Long operatorId, String ipAddress, String requestId, Long tenantId);
}
