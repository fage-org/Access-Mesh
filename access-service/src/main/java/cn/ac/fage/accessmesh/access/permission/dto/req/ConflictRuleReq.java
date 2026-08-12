package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 冲突规则创建请求体
 * <p>
 * 用于创建权限冲突规则，包括冲突类型、涉及的权限和角色。
 * </p>
 *
 * @param conflictType             冲突类型编码，必填（ROLE_MUTEX/PERM_MUTEX）
 * @param firstOperationPermissionId 第一个操作权限ID，可选
 * @param secondOperationPermissionId 第二个操作权限ID，可选
 * @param resourceTypeValue        资源类型值，可选
 * @param firstAbstractRoleId      第一个角色ID，可选
 * @param secondAbstractRoleId     第二个角色ID，可选
 * @param description              规则描述，可选
 */
public record ConflictRuleReq(
    @NotBlank String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description
) {}