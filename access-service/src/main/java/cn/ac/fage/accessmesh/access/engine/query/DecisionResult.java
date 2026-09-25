package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Objects;

/**
 * 最终判定结果（T-PERM-082，设计 §3.1/§3.4）。
 * <p>
 * 拒绝原因内部枚举，不未经版本化直接扩散到普通 SDK；
 * ALLOW 必无 reason、DENY 必有 reason（构造契约）。
 * </p>
 *
 * @param key      项键，非空
 * @param outcome  判定结果，非空
 * @param reason   拒绝原因；ALLOW 时必须为 null，DENY 时必须非空
 * @param coverage 执行覆盖信息，非空
 * @param details  结果详情；null 归一为空详情
 */
public record DecisionResult(String key, Decision outcome, Reason reason,
                             EvaluationCoverage coverage, ResultDetails details) implements ItemResult {

    public DecisionResult {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(outcome, "outcome 不能为空");
        Objects.requireNonNull(coverage, "coverage 不能为空");
        details = details == null ? ResultDetails.empty() : details;
        if (outcome == Decision.ALLOW && reason != null) {
            throw new IllegalArgumentException("ALLOW 结果不携带拒绝原因");
        }
        if (outcome == Decision.DENY && reason == null) {
            throw new IllegalArgumentException("DENY 结果必须携带拒绝原因");
        }
    }

    /** 允许结果。 */
    public static DecisionResult allow(String key, EvaluationCoverage coverage, ResultDetails details) {
        return new DecisionResult(key, Decision.ALLOW, null, coverage, details);
    }

    /** 拒绝结果。 */
    public static DecisionResult deny(String key, Reason reason, EvaluationCoverage coverage, ResultDetails details) {
        return new DecisionResult(key, Decision.DENY, reason, coverage, details);
    }

    /** 判定布尔。 */
    public enum Decision {

        /** 允许。 */
        ALLOW,

        /** 拒绝。 */
        DENY
    }

    /** 拒绝原因（§3.4 原因表；新内部原因经版本化才扩散到 SDK）。 */
    public enum Reason {

        /** 无有效角色。 */
        NO_ROLE,

        /** 未命中任何适用授权（含合法未知目标）。 */
        NO_PERMISSION,

        /** 曾有可评估候选，最终被条件/权限互斥清空（优先于仅父上下文排除）。 */
        CONDITION_NOT_MET_OR_CONFLICT,

        /** 仅有父绑定排除导致拒绝。 */
        DEPENDENT_NOT_IN_PARENT_CONTEXT
    }
}
