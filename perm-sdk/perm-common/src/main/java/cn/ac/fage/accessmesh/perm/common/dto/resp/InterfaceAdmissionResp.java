package cn.ac.fage.accessmesh.perm.common.dto.resp;

/**
 * 操作准入在线判定响应体（T-ACCESS-059，契约总册 §25.2）。
 * <p>
 * AdmissionResult 对外投影。{@code finalCheckRequired} 恒 true——准入永不表示最终许可，
 * 业务服务必须以实际目标做完整实例鉴权（方案 A 两层判定）。MAY_ENTER 不携带拒绝原因。
 * 路由级结果：无注册匹配 DENY（API_NOT_REGISTERED）；多匹配异要求为配置故障
 * 20070（经统一错误信封返回，不进本 DTO）；要求解析失败 20071 同理。
 * </p>
 *
 * @param decision            准入结果 MAY_ENTER / DENY
 * @param reason              拒绝原因（NO_ROLE / NO_CANDIDATE / CONDITION_NOT_MET /
 *                            USER_NOT_FOUND / API_NOT_REGISTERED），MAY_ENTER 时为 null
 * @param requiredPermission  本次路由匹配收敛出的唯一准入要求
 * @param finalCheckRequired  恒 true（准入不是最终许可）
 */
public record InterfaceAdmissionResp(
    String decision,
    String reason,
    AdmissionRequirement requiredPermission,
    boolean finalCheckRequired
) {

    /** MAY_ENTER 快捷构造（finalCheckRequired 恒 true）。 */
    public static InterfaceAdmissionResp mayEnter(AdmissionRequirement requirement) {
        return new InterfaceAdmissionResp("MAY_ENTER", null, requirement, true);
    }

    /** DENY 快捷构造（finalCheckRequired 恒 true）。 */
    public static InterfaceAdmissionResp deny(String reason, AdmissionRequirement requirement) {
        return new InterfaceAdmissionResp("DENY", reason, requirement, true);
    }

    /** 主体未解析（沿 check-interface USER_NOT_FOUND 先例；无要求可回填）。 */
    public static InterfaceAdmissionResp userNotFound() {
        return new InterfaceAdmissionResp("DENY", "USER_NOT_FOUND", null, true);
    }

    /** 路由无注册匹配（要求未知，requiredPermission 为 null）。 */
    public static InterfaceAdmissionResp notRegistered() {
        return new InterfaceAdmissionResp("DENY", "API_NOT_REGISTERED", null, true);
    }
}
