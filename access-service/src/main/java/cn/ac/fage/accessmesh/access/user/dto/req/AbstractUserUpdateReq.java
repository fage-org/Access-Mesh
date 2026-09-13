package cn.ac.fage.accessmesh.access.user.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 用户更新请求体
 * <p>
 * 用于更新用户的信息，包括名称、启用状态和扩展属性。
 * </p>
 *
 * @param userId  用户ID，必填
 * @param name    用户名称，可选
 * @param enabled 是否启用，可选
 * @param extra   扩展属性JSON，可选
 */
public record AbstractUserUpdateReq(
    @NotNull Long userId,
    String name,
    Boolean enabled,
    String extra
) {
    /**
     * 至少一个业务字段（T-ACCESS-034）：空 patch（仅 userId）在旧实现会无门禁落点地
     * 走完写库/投影/审计/缓存失效全链路（无条件写副作用），经 Bean Validation 在
     * Controller 层拒绝（MethodArgumentNotValidException → 90001，HTTP 400）。
     */
    @AssertTrue(message = "至少需要提供一个业务字段（name/enabled/extra）")
    public boolean isAtLeastOneBusinessFieldPresent() {
        return name != null || enabled != null || extra != null;
    }
}