package cn.ac.fage.accessmesh.access.admin.dto.auth;

import java.util.List;

/**
 * 用户信息响应记录类
 * <p>
 * 包含用户的完整信息，包括基本属性、角色列表、权限列表、组织列表。
 * 用于前端获取用户详细信息。
 * </p>
 *
 * @param userId      用户ID
 * @param tenantId    租户ID
 * @param username    用户名
 * @param name        姓名
 * @param phone       手机号
 * @param email       邮箱
 * @param avatar      头像URL
 * @param roles       角色列表
 * @param permissions 权限列表
 * @param orgs        组织列表
 */
public record UserInfoResp(
    /**
     * 用户ID
     */
    Long userId,

    /**
     * 租户ID
     */
    Long tenantId,

    /**
     * 用户名（登录账号）
     */
    String username,

    /**
     * 姓名（显示名称）
     */
    String name,

    /**
     * 手机号
     */
    String phone,

    /**
     * 邮箱
     */
    String email,

    /**
     * 头像URL
     */
    String avatar,

    /**
     * 用户角色列表
     */
    List<RoleInfo> roles,

    /**
     * 用户权限列表（权限编码）
     */
    List<String> permissions,

    /**
     * 用户所属组织列表
     */
    List<OrgInfo> orgs
) {
    /**
     * 角色简要信息记录类
     * <p>
     * 用户所拥有角色的简要信息。
     * </p>
     *
     * @param roleId   角色ID
     * @param roleName 角色名称
     */
    public record RoleInfo(
        /**
         * 角色ID
         */
        Long roleId,

        /**
         * 角色名称
         */
        String roleName
    ) {}

    /**
     * 组织简要信息记录类
     * <p>
     * 用户所属组织的简要信息。
     * </p>
     *
     * @param orgId    组织ID
     * @param orgName  组织名称
     * @param orgType  组织类型
     * @param isPrimary 是否主要组织
     */
    public record OrgInfo(
        /**
         * 组织ID
         */
        Long orgId,

        /**
         * 组织名称
         */
        String orgName,

        /**
         * 组织类型
         */
        String orgType,

        /**
         * 是否主要组织
         */
        boolean isPrimary
    ) {}
}