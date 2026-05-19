package cn.ac.fage.accessmesh.permission.vo;

import java.util.List;

/**
 * 角色权限快照记录类
 * <p>
 * 用于缓存的角色权限数据结构。
 * 包含角色ID、版本号和权限条目列表。
 * </p>
 *
 * @param tenantId 租户ID
 * @param roleId   角色ID
 * @param version  权限版本号，用于缓存一致性检查
 * @param entries  权限条目列表
 */
public record RolePermSnapshot(
    /**
     * 租户ID
     */
    Long tenantId,

    /**
     * 角色ID
     */
    Long roleId,

    /**
     * 权限版本号，用于缓存一致性检查
     */
    long version,

    /**
     * 权限条目列表
     */
    List<RolePermEntry> entries
) {
    /**
     * 角色权限条目记录类
     * <p>
     * 表示单个权限配置的详细信息。
     * 包含资源、操作、条件、授权来源等完整信息。
     * </p>
     * <p>
     * grantedBits 存储 OperationPermission.binaryBit 值，
     * operationCode/effectiveBits 从 OperationPermission 反查填充。
     * </p>
     *
     * @param permissionId         权限ID
     * @param roleId               角色ID
     * @param resourceEntityId     资源实体ID
     * @param resourceCode         资源编码
     * @param resourceType         资源类型值
     * @param grantedBits          授予的操作位值
     * @param operationCode        操作编码
     * @param effectiveBits        有效位掩码
     * @param grantSource          授权来源
     * @param canGrant             是否可授权他人
     * @param conditionId          条件ID
     * @param hasCondition         是否有条件
     * @param dependOn             依赖权限ID
     */
    public record RolePermEntry(
        /**
         * 权限ID
         */
        Long permissionId,

        /**
         * 角色ID
         */
        Long roleId,

        /**
         * 资源实体ID
         */
        Long resourceEntityId,

        /**
         * 资源编码
         */
        String resourceCode,

        /**
         * 资源类型值
         */
        Integer resourceType,

        /**
         * 授予的操作位值
         */
        Long grantedBits,

        /**
         * 操作编码
         */
        String operationCode,

        /**
         * 有效位掩码
         */
        Long effectiveBits,

        /**
         * 授权来源
         */
        String grantSource,

        /**
         * 是否可授权他人
         */
        Boolean canGrant,

        /**
         * 条件ID
         */
        Long conditionId,

        /**
         * 是否有条件
         */
        boolean hasCondition,

        /**
         * 依赖权限ID
         */
        Long dependOn
    ) {}
}