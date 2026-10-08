package cn.ac.fage.accessmesh.access.auth.security;

/** 已验证的平台操作者，用于独立审计；不是租户权限主体。 */
public record PlatformActor(long id, String username) {}
