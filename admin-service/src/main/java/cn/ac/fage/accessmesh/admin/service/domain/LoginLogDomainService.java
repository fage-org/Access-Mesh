package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;

/**
 * 登录日志领域服务
 * 封装登录日志记录核心领域逻辑
 */
public interface LoginLogDomainService {

    /**
     * 记录登录日志
     *
     * @param log 登录日志实体
     */
    void insert(SysLoginLog log);

    /**
     * 记录登录日志（便捷方法）
     *
     * @param tenantId   租户ID
     * @param username   用户名
     * @param clientId   客户端ID
     * @param status     登录状态（1=成功，0=失败）
     * @param failReason 失败原因（成功时为null）
     */
    void recordLoginLog(Long tenantId, String username, String clientId, Integer status, String failReason);
}