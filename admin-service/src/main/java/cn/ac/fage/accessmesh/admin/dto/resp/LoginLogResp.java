package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;

import java.time.LocalDateTime;

/**
 * 登录日志响应记录类
 * <p>
 * 用于返回登录日志查询结果。
 * 隐藏内部字段（tenantId、userId），同时暴露审计相关的PII字段（username、ipAddress）。
 * </p>
 *
 * @param id         登录日志ID
 * @param username   登录用户名
 * @param clientId   客户端ID
 * @param loginType  登录类型（password、sms、oauth2等）
 * @param status     登录状态（0=成功，1=失败）
 * @param ipAddress  IP地址
 * @param userAgent  用户代理（浏览器信息）
 * @param location   登录地点
 * @param failReason 失败原因
 * @param loginAt    登录时间
 */
public record LoginLogResp(
    /**
     * 登录日志ID
     */
    Long id,

    /**
     * 登录用户名
     */
    String username,

    /**
     * 客户端ID
     */
    String clientId,

    /**
     * 登录类型（password、sms、oauth2等）
     */
    String loginType,

    /**
     * 登录状态（0=成功，1=失败）
     */
    Integer status,

    /**
     * IP地址
     */
    String ipAddress,

    /**
     * 用户代理（浏览器信息）
     */
    String userAgent,

    /**
     * 登录地点
     */
    String location,

    /**
     * 失败原因
     */
    String failReason,

    /**
     * 登录时间
     */
    LocalDateTime loginAt
) {
    /**
     * 从实体转换为响应DTO
     *
     * @param entity 登录日志实体
     * @return 登录日志响应DTO
     */
    public static LoginLogResp from(SysLoginLog entity) {
        return new LoginLogResp(
            entity.getId(),
            entity.getUsername(),
            entity.getClientId(),
            entity.getLoginType(),
            entity.getStatus(),
            entity.getIpAddress(),
            entity.getUserAgent(),
            entity.getLocation(),
            entity.getFailReason(),
            entity.getLoginAt()
        );
    }
}