package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.oauth2.*;

public interface OAuth2Service {

    AuthorizeResp authorize(AuthorizeReq req);

    TokenResp token(TokenReq req);

    TokenResp refreshToken(String refreshToken, String clientId, String clientSecret);

    void revokeToken(String accessToken);

    OAuth2UserInfoResp getClientUserInfo(Long userId);
}
