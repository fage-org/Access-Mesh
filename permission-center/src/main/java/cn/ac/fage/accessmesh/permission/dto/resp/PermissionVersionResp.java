package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * Permission version query response.
 */
public record PermissionVersionResp(
    Long roleId,
    String roleTypeCode,
    String roleExternalId,
    long version
) {}
