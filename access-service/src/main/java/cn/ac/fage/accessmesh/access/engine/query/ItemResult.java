package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 项结果封闭变体（T-PERM-082，设计 §3.1）。
 * <p>
 * 三种结果不能只剩一个 allowed：{@link DecisionResult}（ALLOW/DENY）、
 * {@link GrantSetResult}（事实集合状态，无 allowed()）、
 * {@link AdmissionResult}（MAY_ENTER/DENY，恒 finalCheckRequired）。
 * orderedResults 与输入等长、顺序相同；相同目标不同 key 仍各自返回。
 * </p>
 */
public sealed interface ItemResult permits DecisionResult, GrantSetResult, AdmissionResult {

    /** 项键（与 QueryItem.key 对应）。 */
    String key();
}
