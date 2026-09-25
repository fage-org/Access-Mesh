package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 授权清单选择（T-PERM-082，设计 §2.4/§4.6）。
 * <p>
 * 指定主体的一份完整授权事实（视图/转授/范围/旧快照用途）；不是具体业务请求的最终许可。
 * 首版混批约束：GRANT_LIST 单项独占请求。事实完成目标不能被短路或分页破坏（阶段随 T-PERM-086 落地）。
 * </p>
 *
 * @param requiredParent 整清单父门禁要求，可空（无父=保留存储子行参与原清单流程）
 */
public record GrantList(ParentRequirement requiredParent) implements Selection {
}
