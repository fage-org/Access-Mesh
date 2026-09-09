package cn.ac.fage.accessmesh.access.permission.enums;

/**
 * 权限查询目标模式三态（T-PERM-057 统一引擎，query-engine-unification.md §2）。
 * <p>
 * 「无实例目标」不是二义输入——现有契约里它同时承载两种语义，统一入参必须显式区分，
 * 三态互不串义：
 * </p>
 * <ul>
 *   <li>{@link #TYPE_LEVEL}：类型级门禁——无实例目标、只消费 scopeAll
 *       （现 {@code hasPermissionByCode(type, null, op)} 形态，/auth/check 的 resourceCode=null 同义）。
 *       不做实例查询；实例级授权不得使命中（否则任何实例授权都会放行类型级门禁=越权）。</li>
 *   <li>{@link #INSTANCE}：实例判定——带编码/实体 id 目标，scopeAll 类型级命中优先放行，
 *       实例查询按目标下推（判定面继承开启时目标集扩为 {目标}∪同类型祖先链闭包）。</li>
 *   <li>{@link #LIST}：全量清单——无目标、按角色全量拉取角色权限行（清单/视图/范围面）。</li>
 * </ul>
 */
public enum TargetMode {
    TYPE_LEVEL,
    INSTANCE,
    LIST
}
