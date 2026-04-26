package cn.ac.fage.accessmesh.admin.dto.req;

public record OrgUpdateReq(
    Long id,
    String orgName,
    String parentOrgId,
    String code,
    String phone,
    String email,
    Integer status,
    Integer sort
) {}
