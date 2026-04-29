package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Interface snapshot response for Gateway consumption.
 */
public record InterfaceSnapshotResp(
    boolean notModified,
    long currentVersion,
    List<ApiPermissionEntry> allowedApis
) {
    public record ApiPermissionEntry(
        String serviceCode,
        String httpMethod,
        String pathPattern,
        boolean hasCondition,
        Long conditionId
    ) {}
}
