package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.access.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.access.admin.service.AuthService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * <p>
 * 提供用户认证相关功能，包括登录、注销、验证码、用户信息查询等。
 * 使用Sa-Token作为认证框架，支持账号密码登录和短信验证码登录。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/auth")
public class AdminAuthController {

    private final AuthService authService;

    /**
     * 构造函数注入依赖
     *
     * @param authService 认证服务
     */
    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 获取登录验证码
     * <p>
     * 生成图形验证码，用于防止暴力破解登录。
     * 返回验证码图片和验证码Key。
     * </p>
     *
     * @return 验证码响应，包含图片Base64和验证码Key
     */
    @PostMapping("/captcha")
    public PermResult<CaptchaResp> captcha() {
        return PermResult.success(authService.generateCaptcha());
    }

    /**
     * 账号密码登录
     * <p>
     * 使用用户名和密码进行登录认证。
     * 登录成功后返回Sa-Token令牌。
     * </p>
     *
     * @param req 登录请求，包含用户名、密码、验证码
     * @return 登录响应，包含访问令牌和用户基本信息
     */
    @PostMapping("/login")
    public PermResult<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return PermResult.success(authService.login(req));
    }

    /**
     * 短信验证码登录
     * <p>
     * 使用手机号和短信验证码进行登录认证。
     * 用于无密码登录场景。
     * </p>
     *
     * @param req 短信登录请求，包含手机号、验证码
     * @return 登录响应，包含访问令牌和用户基本信息
     */
    @PostMapping("/login/sms")
    public PermResult<LoginResp> smsLogin(@Valid @RequestBody SmsLoginReq req) {
        return PermResult.success(authService.smsLogin(req));
    }

    /**
     * 用户注销
     * <p>
     * 清除用户登录状态，注销Sa-Token会话。
     * </p>
     *
     * @return 操作成功结果
     */
    @PostMapping("/logout")
    public PermResult<Void> logout() {
        authService.logout();
        return PermResult.success();
    }

    /**
     * 获取用户信息
     * <p>
     * 查询当前登录用户的详细信息，包括用户名、头像、角色等。
     * 用于前端展示用户状态。
     * </p>
     *
     * @return 用户信息响应，包含用户详细信息和角色列表
     */
    @PostMapping("/userinfo")
    public PermResult<UserInfoResp> getUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        return PermResult.success(authService.getUserInfo(userId));
    }

    /**
     * 获取用户菜单
     * <p>
     * 查询当前用户有权访问的菜单列表。
     * 用于前端动态生成导航菜单。
     * </p>
     *
     * @return 用户菜单响应，包含菜单树结构
     */
    @PostMapping("/user-menu")
    public PermResult<UserMenuResp> getUserMenu() {
        Long userId = StpUtil.getLoginIdAsLong();
        return PermResult.success(authService.getUserMenu(userId));
    }
}