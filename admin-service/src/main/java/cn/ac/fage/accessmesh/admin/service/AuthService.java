package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.CaptchaResp;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.admin.dto.auth.SmsLoginReq;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.dto.auth.UserMenuResp;

public interface AuthService {

    CaptchaResp generateCaptcha();

    LoginResp login(LoginReq req);

    LoginResp smsLogin(SmsLoginReq req);

    void logout();

    UserInfoResp getUserInfo(Long userId);

    /** 获取用户菜单（动态路由 + 角色 + 按钮权限） */
    UserMenuResp getUserMenu(Long userId);
}
