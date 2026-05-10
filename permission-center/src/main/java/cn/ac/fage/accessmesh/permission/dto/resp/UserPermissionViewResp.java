package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 用户权限视图响应体
 * <p>
 * 返回用户的有效权限视图，按资源分组展示。
 * 用于用户权限查询接口的响应。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码
 * @param subjectExternalId 用户外部标识
 * @param subjectName       用户名称
 * @param resources         资源权限视图列表
 */
public record UserPermissionViewResp(
    String subjectTypeCode,
    String subjectExternalId,
    String subjectName,
    List<ResourcePermissionView> resources
) {
    /**
     * 资源权限视图
     * <p>
     * 表示用户在单个资源上的权限信息。
     * </p>
     *
     * @param resourceEntityId     资源实体ID
     * @param domainCode           业务域编码
     * @param resourceCode         资源编码
     * @param resourceName         资源名称
     * @param resourceTypeCode     资源类型编码
     * @param codeType             编码类型
     * @param scopeAll             是否范围全部
     * @param operationCodes       操作权限编码列表
     * @param sourceRoles          来源角色列表
     * @param sourceRoleCount      来源角色总数
     * @param sourceRolesTruncated 来源角色是否截断（超出显示限制）
     * @param matchedPermissionIds 匹配的权限ID列表
     */
    public record ResourcePermissionView(
        Long resourceEntityId,
        String domainCode,
        String resourceCode,
        String resourceName,
        String resourceTypeCode,
        String codeType,
        boolean scopeAll,
        List<String> operationCodes,
        List<SourceRoleView> sourceRoles,
        int sourceRoleCount,
        boolean sourceRolesTruncated,
        List<Long> matchedPermissionIds
    ) {}

    /**
     * 来源角色视图
     * <p>
     * 表示权限来源的角色信息，包括继承路径。
     * </p>
     *
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部标识
     * @param roleName       角色名称
     * @param via            继承路径列表
     */
    public record SourceRoleView(
        String roleTypeCode,
        String roleExternalId,
        String roleName,
        List<String> via
    ) {}
}