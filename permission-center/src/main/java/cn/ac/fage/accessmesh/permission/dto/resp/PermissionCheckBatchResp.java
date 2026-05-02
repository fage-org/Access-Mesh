package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response for batch permission check (internal use).
 * Returns permission results for each target ID.
 */
public record PermissionCheckBatchResp(
    Map<Long, PermissionCheckResp> results  // key=targetId, value=permission results for that target
) {
    /**
     * Get IDs that have permission for the specified operation.
     */
    public Set<Long> getIdsWithPermission(String operationCode) {
        return results.entrySet().stream()
            .filter(e -> e.getValue().results().getOrDefault(operationCode, false))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Get IDs that lack permission for the specified operation.
     */
    public Set<Long> getIdsWithoutPermission(String operationCode) {
        return results.entrySet().stream()
            .filter(e -> !e.getValue().results().getOrDefault(operationCode, false))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Check if all targets have permission for the specified operation.
     */
    public boolean allHavePermission(String operationCode) {
        return results.values().stream()
            .allMatch(r -> r.results().getOrDefault(operationCode, false));
    }

    /**
     * Check if any target has permission for the specified operation.
     */
    public boolean anyHasPermission(String operationCode) {
        return results.values().stream()
            .anyMatch(r -> r.results().getOrDefault(operationCode, false));
    }
}