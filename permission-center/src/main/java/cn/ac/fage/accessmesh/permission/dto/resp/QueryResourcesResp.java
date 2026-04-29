package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Query-resources response \u2014 accessible resource codes for the subject.
 */
public record QueryResourcesResp(
    List<ResourceEntry> items,
    boolean scopeAll
) {
    public record ResourceEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        boolean canManage
    ) {}
}
