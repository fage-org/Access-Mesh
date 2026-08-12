package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 操作权限更新请求体
 * <p>
 * 用于更新操作权限的信息，包括名称、二进制位和继承掩码。
 * </p>
 *
 * @param operationId 操作权限ID，必填
 * @param name        操作权限名称，可选
 * @param binaryBit   二进制位，可选，用于位运算权限匹配
 * @param inheritMask 继承掩码，可选，用于权限继承计算
 */
public record OperationUpdateReq(
    @NotNull Long operationId,
    String name,
    Long binaryBit,
    Long inheritMask
) {}