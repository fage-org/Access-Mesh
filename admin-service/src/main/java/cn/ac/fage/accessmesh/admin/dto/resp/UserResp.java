package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record UserResp(
    Long id,
    String username,
    String name,
    String phone,
    String email,
    Integer status,
    List<OrgBrief> orgs,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public record OrgBrief(Long orgId, String orgName, String orgType, boolean isPrimary) {}
}
