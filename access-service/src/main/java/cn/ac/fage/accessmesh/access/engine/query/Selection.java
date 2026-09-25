package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 四种选择封闭变体（T-PERM-082，设计 §2.4）。
 * <p>
 * 每种选择都有明确候选范围，不用大量 nullable 字段模拟模式：
 * TYPE_LEVEL（类型级主授权）、TARGET_SET（逐 clause 目标集合）、
 * GRANT_LIST（主体完整授权清单）、OPERATION_ADMISSION（操作准入资格）。
 * </p>
 */
public sealed interface Selection permits TypeLevel, TargetSet, GrantList, OperationAdmission {
}
