package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.oauth2.*;
import cn.ac.fage.accessmesh.admin.service.OAuth2Service;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/oauth2")
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    public OAuth2Controller(OAuth2Service oauth2Service) {
        this.oauth2Service = oauth2Service;
    }

    /**
     * Authorization code grant — requires user to be logged in (Sa-Token session).
     * Returns an authorization code that can be exchanged for an access token.
     */
    @PostMapping("/authorize")
    @AuditLog(module = "OAuth2", action = "授权")
    public PermResult<AuthorizeResp> authorize(@Valid @RequestBody AuthorizeReq req) {
        return PermResult.success(oauth2Service.authorize(req));
    }

    /**
     * Token endpoint — exchanges authorization code for access token.
     * Does NOT require Sa-Token session; validated via client_id + client_secret.
     */
    @PostMapping("/token")
    public PermResult<TokenResp> token(@Valid @RequestBody TokenReq req) {
        return PermResult.success(oauth2Service.token(req));
    }

    /**
     * Refresh token — exchanges a refresh token for a new access token.
     */
    @PostMapping("/refresh")
    public PermResult<TokenResp> refresh(@RequestBody RefreshTokenReq req) {
        return PermResult.success(
            oauth2Service.refreshToken(req.refreshToken(), req.clientId(), req.clientSecret())
        );
    }

    /**
     * Revoke an access token.
     */
    @PostMapping("/revoke")
    @AuditLog(module = "OAuth2", action = "撤销令牌")
    public PermResult<Void> revoke(@RequestBody RevokeTokenReq req) {
        oauth2Service.revokeToken(req.accessToken());
        return PermResult.success();
    }

    /**
     * OAuth2 userinfo endpoint — returns user profile for the current session.
     */
    @PostMapping("/userinfo")
    public PermResult<OAuth2UserInfoResp> userinfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        return PermResult.success(oauth2Service.getClientUserInfo(userId));
    }

    public record RefreshTokenReq(String clientId, String clientSecret, String refreshToken) {}

    public record RevokeTokenReq(String accessToken) {}
}
