package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.dto.auth.UserMenuResp;

/**
 * 认证服务接口
 * <p>
 * 提供用户认证相关的服务方法，包括验证码生成、登录、登出、用户信息查询等。
 * 支持账号密码登录和短信验证码登录两种方式。
 * </p>
 */
public interface AuthService {

    /**
     * 生成验证码
     * <p>
     * 生成用于登录验证的图形验证码。
     * 返回验证码图片和验证码key，用于后续登录验证。
     * </p>
     *
     * @return 验证码响应，包含验证码图片和key
     */
    CaptchaResp generateCaptcha();

    /**
     * 用户登录
     * <p>
     * 使用账号密码进行用户登录验证。
     * 验证成功后返回登录凭证和用户基本信息。
     * </p>
     *
     * @param req 登录请求，包含账号、密码、验证码等
     * @return 登录响应，包含token和用户信息
     */
    LoginResp login(LoginReq req);

    /**
     * 短信验证码登录
     * <p>
     * 使用手机号和短信验证码进行用户登录。
     * 验证成功后返回登录凭证和用户基本信息。
     * </p>
     *
     * @param req 短信登录请求，包含手机号和验证码
     * @return 登录响应，包含token和用户信息
     */
    LoginResp smsLogin(SmsLoginReq req);

    /**
     * 用户登出
     * <p>
     * 注销当前用户的登录状态。
     * 清除session和token相关数据。
     * </p>
     */
    void logout();

    /**
     * 获取用户信息
     * <p>
     * 根据用户ID查询用户详细信息。
     * 包含用户基本信息、角色、权限等。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息响应
     */
    UserInfoResp getUserInfo(Long userId);

    /**
     * 获取用户菜单
     * <p>
     * 获取用户有权访问的菜单列表，用于动态路由配置。
     * 同时返回用户的角色列表和按钮权限标识。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户菜单响应，包含菜单树、角色列表和按钮权限
     */
    UserMenuResp getUserMenu(Long userId);
}