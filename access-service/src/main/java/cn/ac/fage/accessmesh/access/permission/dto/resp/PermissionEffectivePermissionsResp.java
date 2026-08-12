package cn.ac.fage.accessmesh.access.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.List;

/**
 * 有效权限响应体
 * <p>
 * 返回用户或角色的有效权限列表。
 * 统一的条目结构——用户视图填充来源角色，角色视图来源角色为空。
 * 用于有效权限查询接口的响应。
 * </p>
 *
 * @param targetType 目标类型（USER/ROLE）
 * @param items      有效权限条目列表
 * @param total      总记录数
 * @param pageNum    当前页码
 * @param pageSize   每页条数
 * @param hasNext    是否有下一页
 */
public record PermissionEffectivePermissionsResp(
    String targetType,
    List<EffectivePermissionItem> items,
    int total,
    int pageNum,
    int pageSize,
    boolean hasNext
) {
    /**
     * 有效权限条目
     * <p>
     * 表示单个资源的有效权限信息，包括操作权限和来源角色。
     * </p>
     *
     * @param resourceTypeCode     资源类型编码
     * @param resourceCode         资源编码
     * @param resourceName         资源名称
     * @param codeType             编码类型
     * @param operationCodes       操作权限编码列表
     * @param scopeMode            范围模式
     * @param sourceRoles          来源角色列表
     * @param sourceRoleCount      来源角色总数
     * @param sourceRolesTruncated 来源角色是否截断（超出显示限制）
     * @param matchedPermissionIds 匹配的权限ID列表
     */
    public record EffectivePermissionItem(
        String resourceTypeCode,
        String resourceCode,
        String resourceName,
        String codeType,
        List<String> operationCodes,
        ScopeMode scopeMode,
        List<SourceRole> sourceRoles,
        int sourceRoleCount,
        boolean sourceRolesTruncated,
        List<Long> matchedPermissionIds
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
