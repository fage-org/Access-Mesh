package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Objects;

/**
 * 评估策略对（条件×权限互斥，T-PERM-082，设计 §2.5）。
 * <p>
 * 内部条件/权限互斥策略，受合法组合表与受控工厂约束，不是外部安全开关；
 * 与 {@link ResultForm} 的合法配对由 {@code QueryRequestValidator} 在执行前整体校验。
 * </p>
 *
 * @param conditionMode 条件评估模式，非空
 * @param mutexMode     权限互斥模式，非空
 */
public record Evaluation(ConditionMode conditionMode, MutexMode mutexMode) {

    public Evaluation {
        Objects.requireNonNull(conditionMode, "conditionMode 不能为空");
        Objects.requireNonNull(mutexMode, "mutexMode 不能为空");
    }

    /** 普通最终鉴权固定对：EVALUATE＋ENFORCE（DECISION 唯一合法形态）。 */
    public static Evaluation full() {
        return new Evaluation(ConditionMode.EVALUATE, MutexMode.ENFORCE);
    }

    /** 在线操作准入固定对：EVALUATE＋SKIP。 */
    public static Evaluation evaluateSkip() {
        return new Evaluation(ConditionMode.EVALUATE, MutexMode.SKIP);
    }

    /** 准入快照收集固定对：PRESERVE＋SKIP。 */
    public static Evaluation preserveSkip() {
        return new Evaluation(ConditionMode.PRESERVE, MutexMode.SKIP);
    }

    /** 保留条件但执行互斥的清单形态（LEGACY_API 旧快照 PRESERVE＋ENFORCE）。 */
    public static Evaluation preserveEnforce() {
        return new Evaluation(ConditionMode.PRESERVE, MutexMode.ENFORCE);
    }
}
