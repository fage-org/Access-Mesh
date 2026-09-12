package cn.ac.fage.accessmesh.access.permission.dto.req;

/**
 * 冲突规则检测请求体
 * <p>
 * 用于检测指定对象对是否存在冲突/存量持有。两种形态二选一（T-PERM-063 扩展）：
 * 操作权限对（firstOperationPermissionId + secondOperationPermissionId，PERM_MUTEX 场景）
 * 或角色对（firstAbstractRoleId + secondAbstractRoleId，ROLE_MUTEX 场景——检测同时持有
 * 两角色的存量用户，作为立规前预检）。两对都传或都不传由服务端拒绝（VALIDATION_FAILED）。
 * </p>
 *
 * @param firstOperationPermissionId  第一个操作权限ID（操作权限对形态必填）
 * @param secondOperationPermissionId 第二个操作权限ID（操作权限对形态必填）
 * @param resourceTypeValue           资源类型值，可选（仅操作权限对形态有意义）
 * @param firstAbstractRoleId         第一个角色ID（角色对形态必填）
 * @param secondAbstractRoleId        第二个角色ID（角色对形态必填）
 */
public record ConflictRuleDetectReq(
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId
) {}
