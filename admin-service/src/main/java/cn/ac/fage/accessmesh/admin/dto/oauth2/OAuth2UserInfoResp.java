package cn.ac.fage.accessmesh.admin.dto.oauth2;

public record OAuth2UserInfoResp(
    String sub,
    String username,
    String name,
    String phone,
    String email
) {}
