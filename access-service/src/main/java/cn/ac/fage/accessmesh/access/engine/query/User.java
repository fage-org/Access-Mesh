package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 用户主体（内部 userId，T-PERM-082，设计 §2.2）。
 * <p>
 * 外部主体业务键（subjectTypeCode/subjectExternalId）由适配层解析为内部 userId 后构造；
 * 有效角色＋ROLE_MUTEX 过滤在主体解析阶段执行（读支持随 T-PERM-084/085 落地），
 * 普通运行时调用不能关闭角色互斥。
 * </p>
 *
 * @param userId 内部用户 id，必须为正数（结构校验）
 */
public record User(long userId) implements Subject {
}
