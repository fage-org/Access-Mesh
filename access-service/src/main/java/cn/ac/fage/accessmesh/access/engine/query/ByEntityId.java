package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 实体 id 资源引用（T-PERM-082，设计 §2.3）。
 * <p>
 * 仅表示 {@code resource_entity.id}；非正数为结构错误。不做编码反查。
 * </p>
 *
 * @param entityId resource_entity.id，必须为正数
 */
public record ByEntityId(long entityId) implements ResourceRef {
}
