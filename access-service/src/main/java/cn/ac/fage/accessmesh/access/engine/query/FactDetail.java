package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 事实保留档位（T-PERM-082，设计 §3.3）。
 * <p>
 * FACTS 至少 KEPT；范围投影必须 RAW_AND_KEPT；最小 DECISION/ADMISSION 可为 NONE。
 * </p>
 */
public enum FactDetail {

    /** 不保留事实（最小输出）。 */
    NONE,

    /** 仅保留评估后事实（retained）。 */
    KEPT,

    /** 同时保留上下文绑定后原始事实（raw）与评估后事实。 */
    RAW_AND_KEPT
}
