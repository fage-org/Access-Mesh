package cn.ac.fage.accessmesh.access.admin.service.domain;

/**
 * 登录日志领域服务
 * 封装登录日志记录核心领域逻辑
 */
public interface LoginLogDomainService {

    /**
     * 登录日志条目（不可变）。
     *
     * @param tenantId   租户ID
     * @param userId     登录用户ID（用户不存在等失败场景为 null）
     * @param username   登录用户名（SMS 登录成功/命中用户时回填实际用户名，避免手机号入库）
     * @param loginType  登录方式：PASSWORD/SMS/OAUTH2（与 sys_login_log.login_type 注释对齐）
     * @param clientId   客户端ID（OAuth2客户端标识）
     * @param ipAddress  客户端IP（从请求上下文提取，无请求时 null）
     * @param userAgent  User-Agent（从请求上下文提取，无请求时 null）
     * @param status     登录状态（1=成功，0=失败）
     * @param failReason 失败原因（成功时为 null）
     */
    record LoginLogEntry(Long tenantId, Long userId, String username, String loginType,
                         String clientId, String ipAddress, String userAgent,
                         Integer status, String failReason) {}

    /**
     * 记录登录日志
     *
     * @param entry 登录日志条目
     */
    void recordLoginLog(LoginLogEntry entry);
}