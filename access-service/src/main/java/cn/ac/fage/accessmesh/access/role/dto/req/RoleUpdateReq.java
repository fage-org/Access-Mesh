package cn.ac.fage.accessmesh.access.role.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 角色更新请求体
 * <p>
 * 用于更新角色的信息，包括名称、状态、排序和扩展属性。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param roleId    角色ID，必填
 * @param name      角色名称，可选
 * @param status    角色状态，可选，0=禁用，1=启用
 * @param sortOrder 排序顺序，可选
 * @param extra     扩展属性JSON，可选
 * @param extraClear 清空 extra 为 null 的显式标志，可选（JSON null 无法区分「未传」与「清空」，
 *                   T-FE-016 对齐 T-PERM-028 资源域口径；T-API-004 起与 extra 同传拒绝——
 *                   取代旧「true 优先于 extra」的静默丢值口径，U006 拍板全端点冲突拒绝）
 */
public record RoleUpdateReq(
    @NotNull Long roleId,
    String name,
    Integer status,
    Integer sortOrder,
    String extra,
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
