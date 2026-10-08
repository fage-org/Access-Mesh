package cn.ac.fage.accessmesh.access.auth.controller;

import cn.ac.fage.accessmesh.access.auth.dto.CaptchaResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformAccountResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginResp;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformPasswordReq;
import cn.ac.fage.accessmesh.access.auth.service.PlatformAuthAppService;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access/platform-auth")
public class PlatformAuthController {
    private final PlatformAuthAppService service;
    public PlatformAuthController(PlatformAuthAppService service) { this.service=service; }
    @PostMapping("/captcha") public R<CaptchaResp> captcha() { return R.ok(service.captcha()); }
    @PostMapping("/login") public R<PlatformLoginResp> login(@Valid @RequestBody PlatformLoginReq req) { return R.ok(service.login(req)); }
    @PostMapping("/me") public R<PlatformAccountResp> me() { return R.ok(service.me()); }
    @PostMapping("/change-password") public R<Void> changePassword(@Valid @RequestBody PlatformPasswordReq req) {
        service.changePassword(req); return R.ok();
    }
    @PostMapping("/logout") public R<Void> logout(@RequestHeader(value="Authorization",required=false) String header) {
        service.logout(header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null); return R.ok();
    }
}
