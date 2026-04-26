package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * Remove user from organization.
 */
public record UserOrgRemoveReq(Long userId, Long orgId) {}
