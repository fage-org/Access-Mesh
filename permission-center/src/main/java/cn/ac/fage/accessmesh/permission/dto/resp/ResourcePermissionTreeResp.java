package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Tree node for user resource permission tree view.
 */
public record ResourcePermissionTreeResp(
    Long resourceEntityId,
    String domainCode,
    String resourceCode,
    String resourceName,
    String resourceTypeCode,
    String codeType,
    boolean scopeAll,
    List<String> operationCodes,
    List<ResourcePermissionTreeResp> children
) {}
