package cn.ac.fage.accessmesh.access.admin.service;

import cn.ac.fage.accessmesh.access.admin.dto.oauth2.*;

/**
 * OAuth2认证服务接口
 * <p>
 * 提供OAuth2协议相关的认证服务方法，包括授权、令牌获取、令牌刷新等。
 * 支持OAuth2授权码模式和刷新令牌机制。
 * </p>
 */
public interface OAuth2Service {

    /**
     * 授权请求处理
     * <p>
     * 处理OAuth2授权请求，生成授权码。
     * 用于第三方应用发起的授权流程第一步。
     * </p>
     *
     * @param req 授权请求，包含client_id、redirect_uri、scope等
     * @return 授权响应，包含授权码
     */
    AuthorizeResp authorize(AuthorizeReq req);

    /**
     * 令牌获取
     * <p>
     * 根据授权码或刷新令牌获取访问令牌。
     * 用于完成OAuth2授权流程的最后一步。
     * </p>
     *
     * @param req 令牌请求，包含授权码或刷新令牌
     * @return 令牌响应，包含access_token和refresh_token
     */
    TokenResp token(TokenReq req);

    /**
     * 刷新令牌
     * <p>
     * 使用刷新令牌获取新的访问令牌。
     * 用于令牌过期后的无感知续期。
     * </p>
     *
     * @param refreshToken 刷新令牌
     * @param clientId     客户端ID
     * @return 新的令牌响应
     */
    TokenResp refreshToken(String refreshToken, String clientId);

    /**
     * 撤销令牌
     * <p>
     * 撤销指定的访问令牌，使其失效。
     * 用于用户登出或令牌失效场景。
     * </p>
     *
     * @param accessToken 访问令牌
     */
    void revokeToken(String accessToken);

    /**
     * 获取客户端用户信息
     * <p>
     * 根据用户ID获取OAuth2客户端用户的信息。
     * 用于第三方应用获取已授权用户的信息。
     * </p>
     *
     * @param userId 用户ID
     * @return OAuth2用户信息响应
     */
    OAuth2UserInfoResp getClientUserInfo(Long userId);
}