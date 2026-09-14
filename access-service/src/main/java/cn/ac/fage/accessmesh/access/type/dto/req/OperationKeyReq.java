package cn.ac.fage.accessmesh.access.type.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 操作权限业务键请求体
 * <p>
 * 以业务键 (resourceTypeCode, code) 定位操作权限
 * （schema uk_operation_permission_typed 保证唯一，T-PERM-028 切换内部 id 定位；
 * 全局操作概念已退役，resourceTypeCode 必填）。用作 detail 请求体、update 的定位
 * 字段与 remove 的 items 元素。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填
 * @param code             操作编码，必填
 */
public record OperationKeyReq(
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "资源类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String resourceTypeCode,
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "操作编码必须以大写字母开头，仅含大写字母/数字/下划线")
    String code
) {}
