package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 用户有效权限视图响应
 * <p>
 * 用于返回用户的有效权限视图信息。
 * </p>
 */
public record UserPermissionViewResp(
    /**
     * 资源实体ID
     */
    Long resourceEntityId,
    /**
     * 业务域码
     */
    String domainCode,
    /**
     * 资源编码
     */
    String resourceCode,
    /**
     * 资源名称
     */
    String resourceName,
    /**
     * 资源类型码
     */
    String resourceTypeCode,
    /**
     * 编码类型
     */
    String codeType,
    /**
     * 是否全部范围
     */
    boolean scopeAll,
    /**
     * 操作码列表
     */
    List<String> operationCodes,
    /**
     * 来源角色列表
     */
    List<SourceRole> sourceRoles,
    /**
     * 来源角色数量
     */
    int sourceRoleCount,
    /**
     * 来源角色是否被截断
     */
    boolean sourceRolesTruncated,
    /**
     * 匹配的权限ID列表
     */
    List<Long> matchedPermissionIds
) {
    /**
     * 来源角色信息
     * <p>
     * 表示权限来源的角色详情。
     * </p>
     */
    public record SourceRole(
        /**
         * 角色类型码
         */
        String roleTypeCode,
        /**
         * 角色外部ID
         */
        String roleExternalId,
        /**
         * 角色名称
         */
        String roleName,
        /**
         * 授权路径
         */
        List<String> via
    ) {}
}
