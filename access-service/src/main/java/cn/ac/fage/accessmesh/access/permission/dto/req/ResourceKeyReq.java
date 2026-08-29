package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源实体业务键请求体
 * <p>
 * 以业务键 (resourceTypeCode, code, codeType) 定位资源实体
 * （schema uk_resource_entity 保证租户内唯一，T-PERM-028 切换内部 id 定位）。
 * 用作 detail 请求体、move 的 resource/parent 嵌套组件与 remove 的 items 元素。
 * </p>
 *
 * @param resourceTypeCode 资源类型编码，必填
 * @param code             资源编码，必填
 * @param codeType         编码类型，可选；null/空白按 {@link #normalizedCodeType()} 归一为 default
 */
public record ResourceKeyReq(
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    String codeType
) {

    /** DDL resource_entity.code_type 默认值 */
    public static final String CODE_TYPE_DEFAULT = "default";

    /**
     * 归一编码类型：null/空白 → default，去首尾空白
     *
     * @return 归一后的编码类型
     */
    public String normalizedCodeType() {
        return codeType == null || codeType.isBlank() ? CODE_TYPE_DEFAULT : codeType.trim();
    }
}
