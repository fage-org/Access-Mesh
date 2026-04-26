package cn.ac.fage.accessmesh.admin.dto.auth;

import java.util.List;

public record UserInfoResp(
    Long userId,
    Long tenantId,
    String username,
    String name,
    String phone,
    String email,
    String avatar,
    List<RoleInfo> roles,
    List<String> permissions,
    List<OrgInfo> orgs
) {
    public record RoleInfo(Long roleId, String roleName) {}
    public record OrgInfo(Long orgId, String orgName, String orgType, boolean isPrimary) {}
}
