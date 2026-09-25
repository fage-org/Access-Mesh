package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 操作准入选择（T-PERM-082，设计 §2.4/§7）。
 * <p>
 * 一个 type-operation 的候选资格：可来自 ALL 或任意实例上的原授权，
 * 在线评条件但不做实例 PERM_MUTEX/运行时父绑定；不定位本次资源，
 * 不证明有任一实例已完整鉴权成功。仅经 {@code QueryItem.admission/admissionFacts}
 * 受控工厂合法构造（评估策略固定 EVALUATE+SKIP / PRESERVE+SKIP）。
 * </p>
 *
 * @param requirement 唯一类型—操作要求，非空
 */
public record OperationAdmission(TypeOperation requirement) implements Selection {
}
