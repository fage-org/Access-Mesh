package cn.ac.fage.accessmesh.permission.dto.resp;

public record RolePermissionItemResp(
    Long id,
    String resourceTypeCode,
    String resourceCode,
    String codeType,
    String resourceName,
    String operationCode,
    Boolean canGrant,
    String conditionCode,
    boolean scopeAll,
    Long dependOn
) {}
