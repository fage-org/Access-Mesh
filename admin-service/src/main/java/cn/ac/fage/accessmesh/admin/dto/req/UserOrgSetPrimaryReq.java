package cn.ac.fage.accessmesh.admin.dto.req;

/**
 * Set primary organization for user.
 */
public record UserOrgSetPrimaryReq(Long userId, Long orgId) {}
