package cn.ac.fage.accessmesh.access.type.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 操作权限更新请求体
 * <p>
 * 以业务键 (resourceTypeCode, code) 定位待更新操作权限（T-PERM-028），
 * 业务键字段不可更新（稳定编码，原 operationId 定位形态已删除；
 * 全局操作概念已退役，resourceTypeCode 必填）。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填（定位键）
 * @param code             操作编码，必填（定位键）
 * @param name             操作权限名称，可选
 * @param binaryBit        二进制位，可选，用于位运算权限匹配
 * @param inheritMask      继承掩码，可选（null=不更新），用于权限继承计算
 */
public record OperationUpdateReq(
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "资源类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String resourceTypeCode,
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "操作编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String code,
    String name,
    Long binaryBit,
    Long inheritMask
) {}
