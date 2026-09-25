package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Objects;

/**
 * 操作准入结果（T-PERM-082，设计 §3.1/§7）。
 * <p>
 * 不实现最终授权布尔（无 {@code allowed()}）：MAY_ENTER 只表示存在候选资格，
 * 恒要求业务最终检查——{@link #finalCheckRequired()} 固定返回 true（无组件可置 false）。
 * 准入审计 ID 是候选证据，不称为「已访问目标的命中授权」。
 * </p>
 *
 * @param key      项键，非空
 * @param outcome  准入结果，非空
 * @param reason   拒绝原因；MAY_ENTER 时必须为 null，DENY 时必须非空
 * @param coverage 执行覆盖信息，非空
 * @param details  结果详情；null 归一为空详情
 */
public record AdmissionResult(String key, Admission outcome, Reason reason,
                              EvaluationCoverage coverage, ResultDetails details) implements ItemResult {

    public AdmissionResult {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(outcome, "outcome 不能为空");
        Objects.requireNonNull(coverage, "coverage 不能为空");
        details = details == null ? ResultDetails.empty() : details;
        if (outcome == Admission.MAY_ENTER && reason != null) {
            throw new IllegalArgumentException("MAY_ENTER 结果不携带拒绝原因");
        }
        if (outcome == Admission.DENY && reason == null) {
            throw new IllegalArgumentException("DENY 结果必须携带拒绝原因");
        }
    }

    /** 可进入结果（恒要求业务最终检查）。 */
    public static AdmissionResult mayEnter(String key, EvaluationCoverage coverage, ResultDetails details) {
        return new AdmissionResult(key, Admission.MAY_ENTER, null, coverage, details);
    }

    /** 准入拒绝结果。 */
    public static AdmissionResult deny(String key, Reason reason, EvaluationCoverage coverage, ResultDetails details) {
        return new AdmissionResult(key, Admission.DENY, reason, coverage, details);
    }

    /** 恒 true：准入不是最终许可，业务必须做实例最终检查（设计 §7）。 */
    public boolean finalCheckRequired() {
        return true;
    }

    /** 准入结果。 */
    public enum Admission {

        /** 存在候选资格，可进入业务层做最终检查。 */
        MAY_ENTER,

        /** 无准入资格。 */
        DENY
    }

    /** 准入拒绝原因（§3.4；准入不能报告已计算实例冲突）。 */
    public enum Reason {

        /** 无有效角色。 */
        NO_ROLE,

        /** 无覆盖候选。 */
        NO_CANDIDATE,

        /** 候选存在但条件均不通过。 */
        CONDITION_NOT_MET
    }
}
