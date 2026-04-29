package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Query-scopes response \u2014 scope resources (DIRECT \u222a DEPENDENT) within a primary resource context.
 */
public record QueryScopesResp(
    boolean scopeAll,
    List<ScopeEntry> items
) {
    public record ScopeEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        String grantSource
    ) {}
}
