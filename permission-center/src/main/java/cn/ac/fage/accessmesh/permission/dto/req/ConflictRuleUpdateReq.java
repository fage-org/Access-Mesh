package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 冲突规则更新请求体
 * <p>
 * 全量替换语义（PUT）：调用方须传完整字段集（按 conflictType），
 * 后端按 conflictType 全量覆盖对应字段集，对侧字段强制 null。
 * 不支持部分更新（仅更新 description 也须传完整字段集，调用方可先查 detail）。
 * </p>
 * <ul>
 *   <li>conflictType 必填，决定字段集</li>
 *   <li>ROLE_MUTEX：firstAbstractRoleId + secondAbstractRoleId 必填</li>
 *   <li>PERM_MUTEX：firstOperationPermissionId + secondOperationPermissionId 必填，
 *       resourceTypeValue 显式传（null=清空"全部"）</li>
 * </ul>
 *
 * @param id                        规则ID，必填
 * @param conflictType              冲突类型编码，必填（ROLE_MUTEX/PERM_MUTEX）
 * @param firstOperationPermissionId 第一个操作权限ID（PERM_MUTEX 必填）
 * @param secondOperationPermissionId 第二个操作权限ID（PERM_MUTEX 必填）
 * @param resourceTypeValue         资源类型值，PERM_MUTEX 显式传（null=全部资源类型）
 * @param firstAbstractRoleId       第一个角色ID（ROLE_MUTEX 必填）
 * @param secondAbstractRoleId      第二个角色ID（ROLE_MUTEX 必填）
 * @param description               规则描述，可选
 */
public record ConflictRuleUpdateReq(
    @NotNull Long id,
    @NotBlank String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description
) {}
