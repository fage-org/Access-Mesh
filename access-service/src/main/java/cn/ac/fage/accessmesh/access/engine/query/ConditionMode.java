package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 条件评估模式（T-PERM-082，设计 §2.5）。
 * <p>
 * PRESERVE 不等于条件通过，只是不按当前 IP/时间过滤；是否下发条件由投影层处理。
 * </p>
 */
public enum ConditionMode {

    /** 按当前可信环境评估条件。 */
    EVALUATE,

    /** 保留条件事实不过滤（视图/转授/准入快照）。 */
    PRESERVE
}
