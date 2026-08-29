package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 操作权限业务键请求体
 * <p>
 * 以业务键 (resourceTypeCode, code) 定位操作权限
 * （schema uk_operation_permission_typed / uk_operation_permission_global 保证唯一，
 * T-PERM-028 切换内部 id 定位）。resourceTypeCode 为 null/空白表示全局操作。
 * 用作 detail 请求体、update 的定位字段与 remove 的 items 元素。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，可选；null/空白=全局操作
 * @param code             操作编码，必填
 */
public record OperationKeyReq(
    String resourceTypeCode,
    @NotBlank String code
) {

    /**
     * 是否为全局操作（resourceTypeCode null/空白）
     *
     * @return true 表示全局操作
     */
    public boolean isGlobal() {
        return resourceTypeCode == null || resourceTypeCode.isBlank();
    }
}
