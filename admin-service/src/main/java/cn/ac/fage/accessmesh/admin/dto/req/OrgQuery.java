package cn.ac.fage.accessmesh.admin.dto.req;

public record OrgQuery(
    String orgName,
    Integer orgType,
    Integer status,
    String parentOrgId
) {}
