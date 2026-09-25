package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 类型级回退策略（T-PERM-082，设计 §2.4/§9.1，映射旧 exactInstanceOnly）。
 * <p>
 * 与 {@link Inheritance} 独立：SELF 仍可允许类型级回退。
 * 允许回退时 scopeAll 已足以放行（类型级先行短路，不检查实例存在性）；
 * 精确实例配置用 DISALLOW 避免读类型级事实。空实例集合或解析失败永不自动升级为 TYPE_LEVEL。
 * </p>
 */
public enum TypeFallback {

    /** 允许类型级授权回退。 */
    ALLOW,

    /** 不允许类型级回退（实例精准判定）。 */
    DISALLOW
}
