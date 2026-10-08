package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.auth.dto.CaptchaResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformPasswordReq;

public interface PlatformAuthAppService {
    CaptchaResp captcha();
    PlatformLoginResp login(PlatformLoginReq req);
    void logout(String token);
    PlatformAccountResp me();
    void changePassword(PlatformPasswordReq req);
}
