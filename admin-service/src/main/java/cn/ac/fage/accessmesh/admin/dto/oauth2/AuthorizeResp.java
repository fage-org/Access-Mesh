package cn.ac.fage.accessmesh.admin.dto.oauth2;

public record AuthorizeResp(
    String code,
    String state
) {}
