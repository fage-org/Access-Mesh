package cn.ac.fage.accessmesh.access.user.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 用户更新请求体
 * <p>
 * 用于更新用户的信息，包括名称、启用状态和扩展属性。
 * </p>
 *
 * @param userId     用户ID，必填
 * @param name       用户名称，可选
 * @param enabled    是否启用，可选
 * @param extra      扩展属性JSON，可选；空白拒绝，清空须传 extraClear（T-API-004）
 * @param extraClear 扩展属性显式清空标志（T-API-004，协议对齐 role/resource extraClear）：
 *                   true=清空 extra 为 NULL，与 extra 同传拒绝；false/缺省无清空作用
 */
public record AbstractUserUpdateReq(
    @NotNull Long userId,
    String name,
    Boolean enabled,
    @Pattern(regexp = "(?s).*\\S.*", message = "extra 不能为空白；清空请传 extraClear=true")
    String extra,
    Boolean extraClear
) {
    /**
     * 至少一个业务字段（T-ACCESS-034）：空 patch（仅 userId）在旧实现会无门禁落点地
     * 走完写库/投影/审计/缓存失效全链路（无条件写副作用），经 Bean Validation 在
     * Controller 层拒绝（MethodArgumentNotValidException → 90001，HTTP 400）。
     * extraClear=true 是合法业务载荷（T-API-004），计入业务字段。
     */
    @AssertTrue(message = "至少需要提供一个业务字段（name/enabled/extra/extraClear）")
    public boolean isAtLeastOneBusinessFieldPresent() {
        return name != null || enabled != null || extra != null || Boolean.TRUE.equals(extraClear);
    }

    /**
     * T-API-004（U006 拍板：全端点冲突拒绝）：新值与 Clear 同传拒绝。
     */
    @AssertTrue(message = "extra 与 extraClear 不能同时提供（清空请只传 extraClear=true）")
    public boolean isExtraConflictFree() {
        return extra == null || !Boolean.TRUE.equals(extraClear);
    }
}
