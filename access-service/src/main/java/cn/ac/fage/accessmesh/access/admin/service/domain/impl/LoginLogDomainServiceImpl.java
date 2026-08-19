package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysLoginLog;
import cn.ac.fage.accessmesh.access.admin.mapper.SysLoginLogMapper;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录日志领域服务实现类
 * <p>
 * 封装登录日志的数据访问逻辑，提供日志插入和快捷记录方法。
 * 登录日志用于记录用户的登录行为，包括登录时间、方式、状态、失败原因等。
 * 用于安全审计和登录失败追踪。
 * </p>
 * <p>
 * 独立短事务（T-ACCESS-007 §8.2）：{@link #recordLoginLog} 使用 REQUIRES_NEW
 * 在独立事务写入登录日志，登录主流程（认证/失败分支）的异常或回滚不影响日志落库。
 * 方法体不吞异常：REQUIRES_NEW 异常（含 Spring 代理层 commit 阶段的
 * 连接中断/rollback-only）自然传播到调用方，由调用方（AuthServiceImpl.safeRecordLoginLog）
 * 统一 try-catch 兜底隔离，日志失败不影响登录主流程。
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
     * 记录登录日志
     * <p>
     * 完整回填 userId/loginType/IP/User-Agent（T-ACCESS-007）：
     * 由 AuthServiceImpl 从请求上下文提取 IP/UA 后随条目传入，loginType 按登录方式
     * 写入 PASSWORD/SMS/OAUTH2（与 DDL 列注释对齐），不再硬编码小写 password。
     * </p>
     *
     * @param entry 登录日志条目
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginLog(LoginLogEntry entry) {
        SysLoginLog log = new SysLoginLog();
        log.setTenantId(entry.tenantId());
        log.setUserId(entry.userId());
        // 对齐 sys_login_log 列上限截断各自由文本字段：
        // 防止恶意超长输入（超长用户名/clientId/IP/UA/失败原因）触发列值超长导致插入失败，
        // 外层尽管 try-catch 隔离，但整条登录审计会丢失；截断后超长来源仅损失超限部分，审计仍落库。
        log.setUsername(truncate(entry.username(), 64));
        log.setLoginType(truncate(entry.loginType(), 32));
        log.setClientId(truncate(entry.clientId(), 128));
        log.setIpAddress(truncate(entry.ipAddress(), 64));
        log.setUserAgent(truncate(entry.userAgent(), 512));
        log.setStatus(entry.status());
        log.setFailReason(truncate(entry.failReason(), 256));
        log.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    /**
     * 按列上限截断字符串；null 或未超长原样返回。
     */
    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
