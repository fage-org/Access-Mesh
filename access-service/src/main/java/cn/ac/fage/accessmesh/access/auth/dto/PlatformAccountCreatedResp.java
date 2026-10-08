package cn.ac.fage.accessmesh.access.auth.dto;

/** 初始密码仅创建响应展示一次，不提供回查接口。 */
public record PlatformAccountCreatedResp(PlatformAccountResp account, String initialPassword) {}
