package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Objects;

/**
 * 授权事实集合结果（T-PERM-082，设计 §3.1）。
 * <p>
 * 事实完成目标不提供最终授权布尔——本类型无 {@code allowed()} 访问器（三结果不互冒充）。
 * 聚合状态按序判定：NO_ROLE → PARENT_DENIED → 任一阶段有保留事实为 PRESENT →
 * 有 raw 但全部清空为 FILTERED_EMPTY → 其余 NO_MATCH。
 * </p>
 *
 * @param key              项键，非空
 * @param collectionStatus 事实聚合状态，非空
 * @param coverage         执行覆盖信息，非空
 * @param details          结果详情；null 归一为空详情
 */
public record GrantSetResult(String key, CollectionStatus collectionStatus,
                             EvaluationCoverage coverage, ResultDetails details) implements ItemResult {

    public GrantSetResult {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(collectionStatus, "collectionStatus 不能为空");
        Objects.requireNonNull(coverage, "coverage 不能为空");
        details = details == null ? ResultDetails.empty() : details;
    }

    /** 事实聚合状态（§3.1 按序判定）。 */
    public enum CollectionStatus {

        /** 无有效角色。 */
        NO_ROLE,

        /** 父整集合门禁失败。 */
        PARENT_DENIED,

        /** 任一阶段有保留事实。 */
        PRESENT,

        /** 有 raw 候选但全部被条件/互斥清空。 */
        FILTERED_EMPTY,

        /** 无任何命中事实。 */
        NO_MATCH
    }
}
