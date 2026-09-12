package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源实体业务键请求
 * <p>
 * 以业务键 (resourceTypeCode, code, codeType) 定位资源实体（T-PERM-028 定稿，
 * 契约见 api-contract §5.3「业务键定位」）。perm-common 单源契约——服务端
 * Controller/AppService 与 SDK 消费方共用本类（T-PERM-065，PermCommonReqContractTest 快照守卫）。
 * 用作 detail 请求体、move 的 resource/parent 嵌套组件与 remove 的 items 元素。
 * </p>
 */
public record ResourceKeyReq(
    /**
     * 资源类型编码，必填
     */
    @NotBlank String resourceTypeCode,
    /**
     * 资源编码，必填
     */
    @NotBlank String code,
    /**
     * 编码类型，可选；null/空白按 {@link #normalizedCodeType()} 归一为 default
     */
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
