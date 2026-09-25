package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 权限互斥（PERM_MUTEX）模式（T-PERM-082，设计 §2.5）。
 * <p>
 * 仅约束本 item 的条件/权限互斥策略；ROLE_MUTEX 是主体阶段职责，不受本模式影响。
 * 普通 DECISION 不可 SKIP（合法组合表）。
 * </p>
 */
public enum MutexMode {

    /** 对本 item＋阶段的完整候选执行权限互斥。 */
    ENFORCE,

    /** 跳过权限互斥（事实收集/准入用途，由合法组合表约束）。 */
    SKIP
}
