package cn.ac.fage.accessmesh.access.type.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 操作权限创建请求体
 * <p>
 * 用于创建新的操作权限定义，包括资源类型、编码、名称和二进制位。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填
 * @param code             操作权限编码，必填，唯一标识（大写字母开头，仅含大写字母/数字/下划线，T-PERM-066）
 * @param name             操作权限名称，必填
 * @param binaryBit        二进制位，必填，用于位运算权限匹配
 * @param inheritMask      继承掩码，可选，缺省归一为 0（省略与显式 0 等价，T-PERM-077；掩码看位不看正负，不做符号校验）
 */
public record OperationCreateReq(
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "资源类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String resourceTypeCode,
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "操作编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String code,
    @NotBlank String name,
    @NotNull Long binaryBit,
    Long inheritMask
) {}