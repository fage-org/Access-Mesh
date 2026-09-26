package cn.ac.fage.accessmesh.access.engine.query;

/** 展示派生只引用真实授权，不修改其资源、条件、父绑定和来源。 */
public record PresentationEntry(Long sourcePermissionId, Long sourceRoleId, Long displayedEntityId,
                                Derivation derivation) {
    public enum Derivation { ORIGINAL, PARENT, CHILD, OPERATION_COVERAGE }
}
