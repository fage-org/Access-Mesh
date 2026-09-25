package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 资源引用封闭变体（T-PERM-082，设计 §2.3）。
 * <p>
 * {@link ByEntityId} 只表示 {@code resource_entity.id}——sys_user.id、roleId、
 * type_definition.id 不得混入；也不能把 Long 转字符串塞进 {@link ByCode}。
 * </p>
 */
public sealed interface ResourceRef permits ByCode, ByEntityId {
}
