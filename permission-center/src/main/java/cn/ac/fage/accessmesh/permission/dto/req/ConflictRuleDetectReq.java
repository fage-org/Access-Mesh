package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 冲突规则检测请求体
 * <p>
 * 用于检测指定权限之间是否存在冲突。
 * </p>
 *
 * @param firstOperationPermissionId  第一个操作权限ID，必填
 * @param secondOperationPermissionId 第二个操作权限ID，必填
 * @param resourceTypeValue           资源类型值，可选
 */
public record ConflictRuleDetectReq(
    @NotNull Long firstOperationPermissionId,
    @NotNull Long secondOperationPermissionId,
    Integer resourceTypeValue
) {}