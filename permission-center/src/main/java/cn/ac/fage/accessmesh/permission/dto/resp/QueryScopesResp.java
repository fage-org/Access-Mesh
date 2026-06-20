package cn.ac.fage.accessmesh.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.List;

/**
 * 权限范围查询响应体（T-PERM-009 分类模型）
 * <p>
 * 按 {@code (resourceTypeCode, operationCode)} 分类返回数据范围。每格各自 {@link ScopeMode}，
 * 合并原 {@code allowed}（操作层放行）到 {@link ScopeMode}（DENIED 即原 allowed=false）。
 * </p>
 * <p>
 * 业务方按格 {@link ScopeMode} 决定行为：
 * <ul>
 *   <li>{@link ScopeMode#DENIED} — 拒绝/403，不发 SQL</li>
 *   <li>{@link ScopeMode#INSTANCE} — items[] 非空，按 items[] 加 IN 过滤</li>
 *   <li>{@link ScopeMode#ALL} — items[] 为空，不加范围过滤</li>
 *   <li>{@link ScopeMode#EMPTY} — 有权限无数据，返回空结果，不发 SQL</li>
 * </ul>
 * </p>
 *
 * @param reason                 拒绝原因（整体拒绝时填，如 USER_NOT_FOUND/OBJECT_KEY_NOT_FOUND/NO_PERMISSION；允许时为 null）
 * @param matchedParentOperations 匹配的父操作列表（父资源鉴权结果，独立于数据范围）
 * @param parentPermissionIds    父权限ID列表
 * @param scopeGroups            数据范围分组，按 (resourceTypeCode, operationCode) 分类
 * @param permissionVersion      权限版本号（T-PERM-001 收尾移除）
 * @param cacheTtlSeconds        缓存有效时间（秒）
 */
public record QueryScopesResp(
    String reason,
    List<String> matchedParentOperations,
    List<Long> parentPermissionIds,
    List<ScopeGroup> scopeGroups,
    String permissionVersion,
    int cacheTtlSeconds
) {
    /**
     * 数据范围分组（一格 = 一个 resourceType × operation）
     *
     * @param resourceTypeCode 资源类型编码
     * @param operationCode    操作编码
     * @param scopeMode        该格范围模式
     * @param items            资源实例列表（仅 INSTANCE 非空；ALL/DENIED/EMPTY 为空）
     * @param matchedRoleIds   匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     * @param dependOnPermissionIds 依赖的父权限ID列表
     */
    public record ScopeGroup(
        String resourceTypeCode,
        String operationCode,
        ScopeMode scopeMode,
        List<ScopeItem> items,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds,
        List<Long> dependOnPermissionIds
    ) {
    }

    /**
     * 资源实例条目（INSTANCE 模式下的单个资源）
     *
     * @param resourceCode   资源编码
     * @param codeType       编码类型
     * @param resourceName   资源名称
     */
    public record ScopeItem(
        String resourceCode,
        String codeType,
        String resourceName
    ) {
    }
}
