package cn.ac.fage.accessmesh.permission.dto.req;

/**
 * Role tree query. {@code domainCode} null or blank: only roles in the global domain (no biz domain).
 * When set: roles in that domain plus global roles (same semantics as {@link cn.ac.fage.accessmesh.permission.service.impl.RoleManageServiceImpl#getRoleTree}).
 */
public record RoleTreeReq(
    String domainCode
) {}
