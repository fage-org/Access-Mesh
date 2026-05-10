package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 冲突规则更新请求体
 * <p>
 * 用于更新冲突规则的信息，包括冲突类型、涉及的权限和角色。
 * </p>
 *
 * @param id                       规则ID，必填
 * @param bizDomainId              业务域ID，可选
 * @param conflictType             冲突类型编码，可选
 * @param firstOperationPermissionId 第一个操作权限ID，可选
 * @param secondOperationPermissionId 第二个操作权限ID，可选
 * @param resourceTypeValue        资源类型值，可选
 * @param firstAbstractRoleId      第一个角色ID，可选
 * @param secondAbstractRoleId     第二个角色ID，可选
 * @param description              规则描述，可选
 */
public record ConflictRuleUpdateReq(
    @NotNull Long id,
    Long bizDomainId,
    String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description
) {}