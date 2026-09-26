package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 执行阶段（T-PERM-082，设计 §4.2）。
 * <p>
 * TYPE_GRANT/INSTANCE/GRANT_LIST/ADMISSION_CANDIDATES 四个固定阶段；
 * TYPE_GRANT/INSTANCE/GRANT_LIST 已接入执行器；ADMISSION_CANDIDATES 随 T-ACCESS-057
 * （ADM-T02）落地，本枚举为覆盖信息与阶段调度的共享词汇。
 * </p>
 */
public enum Stage {

    /** 类型级主授权阶段（TYPE_LEVEL 项与 typeFallback=ALLOW 的 TARGET_SET 项）。 */
    TYPE_GRANT,

    /** 实例阶段（按 clause 原配对切回候选）。 */
    INSTANCE,

    /** 授权清单阶段（完整事实收集）。 */
    GRANT_LIST,

    /** 操作准入候选阶段。 */
    ADMISSION_CANDIDATES
}
