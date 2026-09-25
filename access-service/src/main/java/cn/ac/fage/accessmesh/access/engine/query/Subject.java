package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 评估主体封闭变体（T-PERM-082，设计 §2.2）。
 * <p>
 * {@link User} 使用有效角色＋ROLE_MUTEX 的共同入口；{@link Roles} 表示可信内部指定角色视角，
 * 不证明用户真实持有、不补加角色、不做 ROLE_MUTEX 过滤（registry 2026-09-22 T-PERM-075
 * 「显式 roleIds 分支不过滤」）。二者不得同时填后再猜优先级——封闭变体在类型层焊死。
 * </p>
 */
public sealed interface Subject permits User, Roles {
}
