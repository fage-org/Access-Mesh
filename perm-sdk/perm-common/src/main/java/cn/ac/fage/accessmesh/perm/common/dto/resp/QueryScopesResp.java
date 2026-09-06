package cn.ac.fage.accessmesh.perm.common.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.util.List;

/**
 * 权限范围查询响应体（T-PERM-009 分类模型；T-API-002 自 access-service 迁入 SDK 公共包）
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
 * <p>
 * T-API-002（2026-09-06 定案）：内部数据库 id 字段族（parentPermissionIds、
 * ScopeGroup 的 matchedRoleIds / matchedPermissionIds / dependOnPermissionIds，
 * 均为 role / role_resource_permission 内部行 id）全数裁剪，与 core-flows §15
 * 「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。父权限 id 集合仅服务端
 * 内部用于 DEPENDENT 子权限过滤，不再出现在响应中。
 * </p>
 *
 * @param reason                 拒绝原因（整体拒绝时填，如 USER_NOT_FOUND/OBJECT_KEY_NOT_FOUND/NO_PERMISSION；允许时为 null）
 * @param matchedParentOperations 匹配的父操作列表（父资源鉴权结果，独立于数据范围）
 * @param scopeGroups            数据范围分组，按 (resourceTypeCode, operationCode) 分类
 * @param cacheTtlSeconds        缓存有效时间（秒）
 */
public record QueryScopesResp(
    String reason,
    List<String> matchedParentOperations,
    List<ScopeGroup> scopeGroups,
    int cacheTtlSeconds
) {
    /**
     * 数据范围分组（一格 = 一个 resourceType × operation）
     *
     * @param resourceTypeCode 资源类型编码
     * @param operationCode    操作编码
     * @param scopeMode        该格范围模式
     * @param items            资源实例列表（仅 INSTANCE 非空；ALL/DENIED/EMPTY 为空）
     */
    public record ScopeGroup(
        String resourceTypeCode,
        String operationCode,
        ScopeMode scopeMode,
        List<ScopeItem> items
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
