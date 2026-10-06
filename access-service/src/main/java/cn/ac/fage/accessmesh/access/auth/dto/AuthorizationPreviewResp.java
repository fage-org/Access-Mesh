package cn.ac.fage.accessmesh.access.auth.dto;

import java.util.List;

/** 已经服务端核验的授权确认页数据，不含授权码或凭据。 */
public record AuthorizationPreviewResp(String clientId, String clientName, String redirectUri,
                                       List<String> scopes, String state) {}
