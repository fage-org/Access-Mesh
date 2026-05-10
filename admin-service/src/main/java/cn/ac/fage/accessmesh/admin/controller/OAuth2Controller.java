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

/**
 * OAuth2认证控制器
 * <p>
 * 提供OAuth2授权码流程的授权、令牌交换、刷新、撤销等功能。
 * 支持第三方应用通过OAuth2协议接入系统，获取用户授权后的访问令牌。
 * 授权流程：用户登录 -> 授权 -> 获取授权码 -> 交换令牌。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/auth/oauth2")
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    /**
     * 构造函数注入依赖
     *
     * @param oauth2Service OAuth2服务
     */
    public OAuth2Controller(OAuth2Service oauth2Service) {
        this.oauth2Service = oauth2Service;
    }

    /**
     * OAuth2授权接口
     * <p>
     * 用户登录后调用此接口授权第三方应用访问。
     * 返回授权码，第三方应用可使用授权码换取访问令牌。
     * 需要用户已登录（有Sa-Token会话）。
     * </p>
     *
     * @param req 授权请求，包含客户端ID、重定向URI、授权范围
     * @return 授权响应，包含授权码
     */
    @PostMapping("/authorize")
    @AuditLog(module = "OAuth2", action = "授权")
    public PermResult<AuthorizeResp> authorize(@Valid @RequestBody AuthorizeReq req) {
        return PermResult.success(oauth2Service.authorize(req));
    }

    /**
     * OAuth2令牌接口
     * <p>
     * 第三方应用使用授权码换取访问令牌。
     * 不需要用户登录，通过客户端ID和密钥验证应用身份。
     * </p>
     *
     * @param req 令牌请求，包含授权码、客户端ID、客户端密钥、重定向URI
     * @return 令牌响应，包含访问令牌、刷新令牌、过期时间
     */
    @PostMapping("/token")
    public PermResult<TokenResp> token(@Valid @RequestBody TokenReq req) {
        return PermResult.success(oauth2Service.token(req));
    }

    /**
     * OAuth2刷新令牌接口
     * <p>
     * 使用刷新令牌获取新的访问令牌。
     * PKCE公开客户端只需客户端ID和刷新令牌，无需客户端密钥。
     * </p>
     *
     * @param req 刷新令牌请求，包含客户端ID和刷新令牌
     * @return 令牌响应，包含新的访问令牌和刷新令牌
     */
    @PostMapping("/refresh")
    public PermResult<TokenResp> refresh(@RequestBody RefreshTokenReq req) {
        return PermResult.success(
            oauth2Service.refreshToken(req.refreshToken(), req.clientId())
        );
    }

    /**
     * OAuth2撤销令牌接口
     * <p>
     * 撤销访问令牌，使令牌失效。
     * 用于用户主动取消授权或安全退出。
     * </p>
     *
     * @param req 撤销令牌请求，包含访问令牌
     * @return 操作成功结果
     */
    @PostMapping("/revoke")
    @AuditLog(module = "OAuth2", action = "撤销令牌")
    public PermResult<Void> revoke(@RequestBody RevokeTokenReq req) {
        oauth2Service.revokeToken(req.accessToken());
        return PermResult.success();
    }

    /**
     * OAuth2用户信息接口
     * <p>
     * 第三方应用使用访问令牌获取用户基本信息。
     * 返回用户ID、用户名、头像等信息。
     * </p>
     *
     * @return 用户信息响应，包含用户基本信息
     */
    @PostMapping("/userinfo")
    public PermResult<OAuth2UserInfoResp> userinfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        return PermResult.success(oauth2Service.getClientUserInfo(userId));
    }

    /**
     * 刷新令牌请求记录
     *
     * @param clientId    客户端ID
     * @param refreshToken 刷新令牌
     */
    public record RefreshTokenReq(String clientId, String refreshToken) {}

    /**
     * 撤销令牌请求记录
     *
     * @param accessToken 访问令牌
     */
    public record RevokeTokenReq(String accessToken) {}
}