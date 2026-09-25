package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 结果完成目标（T-PERM-082，设计 §2.5/§3.1）。
 * <p>
 * 三种完成目标不可相互冒充：GrantSetResult 无 allowed()，
 * AdmissionResult 不实现最终授权布尔且恒 finalCheckRequired=true。
 * </p>
 */
public enum ResultForm {

    /** 普通最终鉴权布尔（TYPE_LEVEL/TARGET_SET 专用）。 */
    DECISION,

    /** 授权事实收集（全部 Selection 可用，按矩阵约束评估策略）。 */
    FACTS,

    /** 操作准入（OPERATION_ADMISSION 专用，恒要求业务最终检查）。 */
    ADMISSION
}
