package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 单条权限校验响应
 * <p>
 * 包含校验结果、拒绝原因、匹配的角色和权限信息。
 * </p>
 */
public record AuthCheckResp(
    /**
     * 是否允许通过
     */
    boolean allowed,
    /**
     * 拒绝原因：NO_PERMISSION、USER_NOT_FOUND 等
     */
    String reason,
    /**
     * 匹配的角色ID列表
     */
    List<Long> matchedRoleIds,
    /**
     * 匹配的权限ID列表
     */
    List<Long> matchedPermissionIds,
    /**
     * 是否完成条件评估
     */
    boolean conditionEvaluated
) {

    /**
     * 创建允许通过的响应
     *
     * @param matchedRoleIds       匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     * @param conditionEvaluated   是否完成条件评估
     * @return 允许通过的权限校验响应
     */
    public static AuthCheckResp allow(List<Long> matchedRoleIds, List<Long> matchedPermissionIds, boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, matchedRoleIds, matchedPermissionIds, conditionEvaluated);
    }

    /**
     * 创建拒绝通过的响应
     *
     * @param reason 拒绝原因
     * @return 拒绝通过的权限校验响应
     */
    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, List.of(), List.of(), false);
    }
}