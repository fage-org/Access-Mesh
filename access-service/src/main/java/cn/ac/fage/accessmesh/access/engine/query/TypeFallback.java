package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 类型级回退策略（T-PERM-082，设计 §2.4/§9.1，映射旧 exactInstanceOnly）。
 * <p>
 * 与 {@link Inheritance} 独立：SELF 仍可允许类型级回退。
 * 普通目标允许回退时 scopeAll 已足以放行；带主上下文的关联类型先核验实际目标的关系和状态，
 * 再进入类型级快速路径。
 * 精确实例配置用 DISALLOW 避免读类型级事实。空实例集合或解析失败永不自动升级为 TYPE_LEVEL。
 * </p>
 */
public enum TypeFallback {

    /** 允许类型级授权回退。 */
    ALLOW,

    /** 不允许类型级回退（实例精准判定）。 */
    DISALLOW
}
