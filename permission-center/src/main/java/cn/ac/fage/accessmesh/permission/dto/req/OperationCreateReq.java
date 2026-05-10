package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 操作权限创建请求体
 * <p>
 * 用于创建新的操作权限，包括资源类型、编码、名称和二进制位。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填
 * @param code             操作权限编码，必填，唯一标识
 * @param name             操作权限名称，必填
 * @param binaryBit        二进制位，必填，用于位运算权限匹配
 * @param inheritMask      继承掩码，可选，用于权限继承计算
 */
public record OperationCreateReq(
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    @NotBlank String name,
    @NotNull Long binaryBit,
    Long inheritMask
) {}