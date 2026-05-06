package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.admin.service.domain.LoginLogDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoginLogDomainServiceImpl implements LoginLogDomainService {

    private final SysLoginLogMapper loginLogMapper;

    public LoginLogDomainServiceImpl(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @Override
    public void insert(SysLoginLog log) {
        if (log == null) {
            return;
        }
        loginLogMapper.insert(log);
    }

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