package cn.ac.fage.accessmesh.access.permission.vo;

/**
 * 角色权限条目记录类（T-PERM-018 提升为顶层 record）。
 * <p>
 * 表示单个权限配置的详细信息，包含资源、操作、条件、授权来源等完整信息。
 * 作为 {@code ROLE_PERM_SNAPSHOT} 缓存值 {@code List<RolePermEntry>} 的元素（条件评估前、
 * 互斥过滤前的原始权限记录），由 {@code PermQueryEngine} LIST 模式读路径（loadRolePermEntriesWithCache）消费。
 * </p>
 * <p>
 * grantedBits 存储 OperationPermission.binaryBit 值，
 * operationCode/effectiveBits 从 OperationPermission 反查填充。
 * </p>
 *
 * @param permissionId     权限ID
 * @param roleId           角色ID
 * @param resourceEntityId 资源实体ID
 * @param resourceCode     资源编码
 * @param resourceType     资源类型值
 * @param grantedBits      授予的操作位值
 * @param operationCode    操作编码
 * @param effectiveBits    有效位掩码
 * @param grantSource      授权来源
 * @param canGrant         是否可授权他人
 * @param conditionId      条件ID
 * @param hasCondition     是否有条件
 * @param dependOn         依赖权限ID
 * @param scopeAll         是否全部范围（true=全部范围，false=限定范围）
 */
public record RolePermEntry(
    Long permissionId,
    Long roleId,
    Long resourceEntityId,
    String resourceCode,
    Integer resourceType,
    Long grantedBits,
    String operationCode,
    Long effectiveBits,
    String grantSource,
    Boolean canGrant,
    Long conditionId,
    boolean hasCondition,
    Long dependOn,
    Boolean scopeAll
) {}
