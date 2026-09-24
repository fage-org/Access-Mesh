package cn.ac.fage.accessmesh.access.type.dto.req;

import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 类型定义更新请求体
 * <p>
 * 用于更新类型定义的信息，包括名称、描述、排序等。
 * </p>
 *
 * @param typeId          类型定义ID，必填
 * @param name            类型名称，可选
 * @param description     类型描述，可选；空白拒绝，清空须传 descriptionClear（T-API-004）
 * @param sortOrder       排序顺序，可选
 * @param extra           扩展属性JSON，可选；空白拒绝；不支持清空（extraClear 任何非 null 值拒绝——
 *                        含服务端管理键 managedMode/syncSourceService/grantOriginRole，U006 拍板）
 * @param descriptionClear 描述显式清空标志（T-API-004）：true=清空 description 为 NULL，
 *                        与 description 同传拒绝；false/缺省无清空作用
 * @param extraClear      恒拒绝字段（T-API-004/U006 拍板「type extra 拒绝清空」）：任何非 null 值
 *                        由服务层 400 拒绝——占位声明意图，给误传调用方明确错误而非静默忽略
 */
public record TypeUpdateReq(
    @NotNull Long typeId,
    String name,
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "description 不能为空白；清空请传 descriptionClear=true")
    String description,
    Integer sortOrder,
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "extra 不能为空白；不支持清空（含服务端管理键）")
    String extra,
    Boolean descriptionClear,
    Boolean extraClear
) {
    /**
     * T-API-004（U006 拍板：全端点冲突拒绝）：新值与 Clear 同传拒绝。
     */
    @AssertTrue(message = "description 与 descriptionClear 不能同时提供（清空请只传 descriptionClear=true）")
    @JsonIgnore
    public boolean isDescriptionConflictFree() {
        return description == null || !Boolean.TRUE.equals(descriptionClear);
    }
}
