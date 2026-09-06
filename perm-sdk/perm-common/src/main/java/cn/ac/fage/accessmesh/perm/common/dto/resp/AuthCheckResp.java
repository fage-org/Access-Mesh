package cn.ac.fage.accessmesh.perm.common.dto.resp;

/**
 * 单条权限校验响应
 * <p>
 * 包含校验结果与拒绝原因。
 * </p>
 * <p>
 * T-API-002（2026-09-06 定案，用户决策扩大裁剪面）：内部数据库 id 字段族
 * （matchedRoleIds / matchedPermissionIds，role / role_resource_permission 内部行 id）
 * 裁剪，与 core-flows §15「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。
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
     * 是否完成条件评估
     */
    boolean conditionEvaluated
) {

    /**
     * 创建允许通过的响应
     *
     * @param conditionEvaluated   是否完成条件评估
     * @return 允许通过的权限校验响应
     */
    public static AuthCheckResp allow(boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, conditionEvaluated);
    }

    /**
     * 创建拒绝通过的响应
     *
     * @param reason 拒绝原因
     * @return 拒绝通过的权限校验响应
     */
    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, false);
    }
}
