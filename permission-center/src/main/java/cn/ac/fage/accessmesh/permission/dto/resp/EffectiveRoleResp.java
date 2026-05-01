package cn.ac.fage.accessmesh.permission.dto.resp;

public record EffectiveRoleResp(
    String roleTypeCode,
    String roleExternalId,
    String roleName
) {}
