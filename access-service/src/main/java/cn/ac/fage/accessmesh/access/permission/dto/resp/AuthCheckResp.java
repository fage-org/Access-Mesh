package cn.ac.fage.accessmesh.access.permission.dto.resp;

/**
 * 单次权限校验响应体
 * <p>
 * 返回单次权限校验的结果，包括是否允许与拒绝原因。
 * 用于权限检查接口的响应。
 * </p>
 * <p>
 * T-API-002（2026-09-06 定案，用户决策扩大裁剪面）：内部数据库 id 字段族
 * （matchedRoleIds / matchedPermissionIds，role / role_resource_permission 内部行 id）
 * 裁剪，与 core-flows §15「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。
 * 线格式与 perm-common 副本保持同形（双副本形状由回归锁钉死）。
 * </p>
 *
 * @param allowed           是否允许访问
 * @param reason            拒绝原因，允许时为null
 * @param conditionEvaluated 是否评估了条件权限
 */
public record AuthCheckResp(
    boolean allowed,
    String reason,
    boolean conditionEvaluated
) {
    /**
     * 创建允许的响应
     * <p>
     * 权限校验通过时的响应。
     * </p>
     *
     * @param conditionEvaluated 是否评估了条件权限
     * @return 允许的响应对象
     */
    public static AuthCheckResp allow(boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, conditionEvaluated);
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
        return new AuthCheckResp(false, reason, false);
    }
}
