package org.dromara.auth.service;

import org.dromara.system.api.model.LoginUser;

/**
 * 登录增强服务
 *
 * 在登录时查询权限版本和主体映射
 *
 * @author RuoYi-Cloud-Plus
 */
public interface LoginEnhancementService {

    /**
     * 增强登录用户信息
     *
     * 查询权限版本和抽象用户ID，设置到 LoginUser
     *
     * @param loginUser 登录用户
     */
    void enhance(LoginUser loginUser);
}
