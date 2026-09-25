package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 目标子句（T-PERM-082，设计 §2.3）。
 * <p>
 * 一个 clause 绑定一个类型—操作配对与一个资源引用；物理 SQL 可装载超集，
 * 但候选选择必须还原原配对（不能因 Y 需要 EDIT 而让 X/EDIT 串入）。
 * </p>
 *
 * @param operation 类型—操作配对，非空
 * @param resource  资源引用，非空
 */
public record TargetClause(TypeOperation operation, ResourceRef resource) {
}
