package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.admin.service.domain.LoginLogDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录日志领域服务实现类
 * <p>
 * 封装登录日志的数据访问逻辑，提供日志插入和快捷记录方法。
 * 登录日志用于记录用户的登录行为，包括登录时间、方式、状态、失败原因等。
 * 用于安全审计和登录失败追踪。
 * </p>
 */
@Service
public class LoginLogDomainServiceImpl implements LoginLogDomainService {

    private final SysLoginLogMapper loginLogMapper;

    /**
     * 构造函数注入依赖
     *
     * @param loginLogMapper 登录日志数据访问层
     */
    public LoginLogDomainServiceImpl(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 快捷记录登录日志
     * <p>
     * 创建并插入一条登录日志，记录登录行为。
     * 默认登录方式为密码登录（password）。
     * 用于登录成功/失败时的快捷日志记录。
     * </p>
     *
     * @param tenantId   租户ID
     * @param username   登录用户名
     * @param clientId   客户端ID（OAuth2客户端标识）
     * @param status     登录状态（1成功，0失败）
     * @param failReason 失败原因，成功时为null
     */
    @Override
    public void recordLoginLog(Long tenantId, String username, String clientId, Integer status, String failReason) {
        SysLoginLog log = new SysLoginLog();
        log.setTenantId(tenantId);
        log.setUsername(username);
        log.setLoginType("password");
        log.setClientId(clientId);
        log.setStatus(status);
        log.setFailReason(failReason);
        log.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }
}