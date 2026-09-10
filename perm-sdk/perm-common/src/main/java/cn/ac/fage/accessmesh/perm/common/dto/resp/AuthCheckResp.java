package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 单条权限校验响应
 * <p>
 * 包含校验结果、拒绝原因与命中的结果记录。
 * </p>
 * <p>
 * T-API-003（2026-09-09 定案，推翻 T-API-002 的 check 族裁剪）：结果记录
 * （matchedRoleIds / matchedPermissionIds，role / role_resource_permission 内部行 id）
 * 全量回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场。
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
     * 匹配的角色ID列表（拒绝时为空列表）
     */
    List<Long> matchedRoleIds,
    /**
     * 匹配的权限ID列表（拒绝时为空列表）
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
     * @param matchedRoleIds      匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     * @param conditionEvaluated   是否完成条件评估
     * @return 允许通过的权限校验响应
     */
    public static AuthCheckResp allow(List<Long> matchedRoleIds, List<Long> matchedPermissionIds,
                                      boolean conditionEvaluated) {
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
