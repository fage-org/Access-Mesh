package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 单次权限校验响应体
 * <p>
 * 返回单次权限校验的结果，包括是否允许、拒绝原因、匹配的角色和权限。
 * 用于权限检查接口的响应。
 * </p>
 *
 * @param allowed           是否允许访问
 * @param reason            拒绝原因，允许时为null
 * @param matchedRoleIds    匹配的角色ID列表
 * @param matchedPermissionIds 匹配的权限ID列表
 * @param conditionEvaluated 是否评估了条件权限
 */
public record AuthCheckResp(
    boolean allowed,
    String reason,
    List<Long> matchedRoleIds,
    List<Long> matchedPermissionIds,
    boolean conditionEvaluated
) {
    /**
     * 创建允许的响应
     * <p>
     * 权限校验通过时的响应。
     * </p>
     *
     * @param matchedRoleIds    匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     * @param conditionEvaluated 是否评估了条件权限
     * @return 允许的响应对象
     */
    public static AuthCheckResp allow(List<Long> matchedRoleIds, List<Long> matchedPermissionIds, boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, matchedRoleIds, matchedPermissionIds, conditionEvaluated);
    }

    
    /**
     * 创建拒绝的响应
     * <p>
     * 权限校验失败时的响应。
     * </p>
     *
     * @param reason 拒绝原因
     * @return 拒绝的响应对象
     */
    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, List.of(), List.of(), false);
    }
}