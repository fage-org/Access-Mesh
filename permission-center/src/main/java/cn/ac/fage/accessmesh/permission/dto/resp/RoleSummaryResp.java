package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * Lightweight role summary returned by extra-roles/list.
 */
public record RoleSummaryResp(
    Long id,
    String roleTypeCode,
    String externalId,
    String name
) {}
