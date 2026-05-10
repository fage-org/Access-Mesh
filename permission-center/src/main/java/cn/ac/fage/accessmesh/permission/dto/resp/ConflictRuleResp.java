package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 冲突规则响应体
 * <p>
 * 返回权限冲突规则的详细信息，包括冲突类型、涉及的角色和权限等。
 * 用于冲突规则查询接口的响应。
 * </p>
 *
 * @param id                       规则ID
 * @param tenantId                 租户ID
 * @param bizDomainId              业务域ID
 * @param conflictType             冲突类型编码（ROLE_MUTEX/PERM_MUTEX）
 * @param firstOperationPermissionId  第一个操作权限ID
 * @param secondOperationPermissionId 第二个操作权限ID
 * @param resourceTypeValue        资源类型值
 * @param firstAbstractRoleId      第一个角色ID
 * @param secondAbstractRoleId     第二个角色ID
 * @param description              规则描述
 * @param createdAt                创建时间
 */
public record ConflictRuleResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description,
    LocalDateTime createdAt
) {}