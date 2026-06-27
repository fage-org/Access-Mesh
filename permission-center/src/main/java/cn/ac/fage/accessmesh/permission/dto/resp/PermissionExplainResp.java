package cn.ac.fage.accessmesh.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.List;

/**
 * 权限解释响应体
 * <p>
 * 返回权限判定的详细解释信息，包括权限来源和最近变更。
 * 用于权限解释查询接口的响应，帮助用户理解权限判定原因。
 * </p>
 *
 * @param targetType         目标类型
 * @param allowed            是否允许访问
 * @param reason             拒绝原因，允许时为null
 * @param permission         权限键信息
 * @param sourceRoles        来源角色列表
 * @param matchedPermissionIds 匹配的权限ID列表
 * @param recentChanges      最近变更列表
 */
public record PermissionExplainResp(
    String targetType,
    boolean allowed,
    String reason,
    PermissionKey permission,
    List<SourceRole> sourceRoles,
    List<Long> matchedPermissionIds,
    List<RecentChangeResp> recentChanges
) {
    /**
     * 权限键信息
     * <p>
     * 表示权限的唯一标识信息。
     * </p>
     *
     * @param domainCode        业务域编码
     * @param resourceTypeCode  资源类型编码
     * @param resourceCode      资源编码
     * @param codeType          编码类型
     * @param operationCode     操作编码
     * @param scopeMode         范围模式
     */
    public record PermissionKey(
        String domainCode,
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        ScopeMode scopeMode
    ) {}

    /**
     * 来源角色信息
     * <p>
     * 表示权限来源的角色信息，包括继承路径。
     * </p>
     *
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部标识
     * @param roleName       角色名称
     * @param via            继承路径列表
     */
    public record SourceRole(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}
}
