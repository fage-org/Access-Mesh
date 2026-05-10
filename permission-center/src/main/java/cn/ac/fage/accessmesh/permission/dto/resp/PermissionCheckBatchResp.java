package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量权限检查响应体
 * <p>
 * 统一批量权限检查的响应，返回每个目标ID的权限结果。
 * 用于内部批量权限检查的响应。
 * </p>
 *
 * @param results 权限结果映射，key=目标ID，value=权限检查结果
 */
public record PermissionCheckBatchResp(
    Map<Long, PermissionCheckResp> results
) {
    /**
     * 获取拥有指定操作权限的ID集合
     * <p>
     * 筛选出所有拥有指定操作权限的目标ID。
     * </p>
     *
     * @param operationCode 操作编码
     * @return 拥有权限的ID集合
     */
    public Set<Long> getIdsWithPermission(String operationCode) {
        return results.entrySet().stream()
            .filter(e -> e.getValue().results().getOrDefault(operationCode, false))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * 获取缺少指定操作权限的ID集合
     * <p>
     * 筛选出所有缺少指定操作权限的目标ID。
     * </p>
     *
     * @param operationCode 操作编码
     * @return 缺少权限的ID集合
     */
    public Set<Long> getIdsWithoutPermission(String operationCode) {
        return results.entrySet().stream()
            .filter(e -> !e.getValue().results().getOrDefault(operationCode, false))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * 检查是否所有目标都拥有指定操作权限
     *
     * @param operationCode 操作编码
     * @return 是否所有目标都拥有权限
     */
    public boolean allHavePermission(String operationCode) {
        return results.values().stream()
            .allMatch(r -> r.results().getOrDefault(operationCode, false));
    }

    /**
     * 检查是否任意目标拥有指定操作权限
     *
     * @param operationCode 操作编码
     * @return 是否任意目标拥有权限
     */
    public boolean anyHasPermission(String operationCode) {
        return results.values().stream()
            .anyMatch(r -> r.results().getOrDefault(operationCode, false));
    }
}