package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record OrgResp(
    Long id,
    Integer orgType,
    String orgName,
    String parentOrgId,
    String code,
    String phone,
    String email,
    Integer status,
    Integer sort,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<OrgResp> children
) {}
