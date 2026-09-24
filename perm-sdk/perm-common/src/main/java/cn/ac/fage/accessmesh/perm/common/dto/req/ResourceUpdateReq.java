package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * 资源更新请求
 * <p>
 * 以业务键 (resourceTypeCode, code, codeType) 定位待更新资源（T-PERM-028 定稿，
 * 契约见 api-contract 总册 §12.1「业务键定位」），业务键字段不可更新（编码为稳定标识；
 * 原 id 定位 + code 可更新形态已删除）。extraClear 用于显式清空 extra（JSON null
 * 无法区分「未传」与「清空」）；T-API-004 起与 extra 同传拒绝——取代旧「true 优先于
 * extra」的静默丢值口径（U006 拍板全端点冲突拒绝）。
 * </p>
 */
public record ResourceUpdateReq(
    /**
     * 资源类型编码，必填（定位键）
     */
    @NotBlank String resourceTypeCode,
    /**
     * 资源编码，必填（定位键，不可更新）
     */
    @NotBlank String code,
    /**
     * 编码类型，可选；缺省 default（定位键）
     */
    String codeType,
    /**
     * 资源名称，可选
     */
    String name,
    /**
     * 资源路径，可选
     */
    String path,
    /**
     * 资源状态，可选，0=禁用，1=启用
     */
    Integer status,
    /**
     * 扩展属性 JSON，可选；null=不更新
     */
    String extra,
    /**
     * 清空 extra 为 null 的显式标志，可选；与 extra 同传拒绝（T-API-004）
     */
    Boolean extraClear
) {
    /**
     * T-API-004（U006 拍板：全端点冲突拒绝）：新值与 Clear 同传拒绝。
     */
    @AssertTrue(message = "extra 与 extraClear 不能同时提供（清空请只传 extraClear=true）")
    public boolean isExtraConflictFree() {
        return extra == null || !Boolean.TRUE.equals(extraClear);
    }
}
