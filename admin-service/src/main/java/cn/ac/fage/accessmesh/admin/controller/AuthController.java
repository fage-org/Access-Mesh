package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.admin.service.AuthService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/captcha")
    public PermResult<CaptchaResp> captcha() {
        return PermResult.success(authService.generateCaptcha());
    }

    @PostMapping("/login")
    public PermResult<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return PermResult.success(authService.login(req));
    }

    @PostMapping("/login/sms")
    public PermResult<LoginResp> smsLogin(@Valid @RequestBody SmsLoginReq req) {
        return PermResult.success(authService.smsLogin(req));
    }

    @PostMapping("/logout")
    public PermResult<Void> logout() {
        authService.logout();
        return PermResult.success();
    }

    @PostMapping("/userinfo")
    public PermResult<UserInfoResp> getUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        return PermResult.success(authService.getUserInfo(userId));
    }

    @PostMapping("/user-menu")
    public PermResult<UserMenuResp> getUserMenu() {
        Long userId = StpUtil.getLoginIdAsLong();
        return PermResult.success(authService.getUserMenu(userId));
    }
}
