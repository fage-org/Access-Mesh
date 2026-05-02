package cn.ac.fage.accessmesh.permission.constant;

/**
 * Permission module constants.
 */
public final class PermConstants {

    private PermConstants() {}

    /**
     * Target type constants for UserRole.targetType
     */
    public interface TargetType {
        String GROUP_ROLE = "GROUP_ROLE";
        String ROLE = "ROLE";
        String USER = "USER";
    }

    /**
     * Maintain source constants for ResourceEntity.maintainSource
     */
    public interface MaintainSource {
        String SERVICE_SYNC = "SERVICE_SYNC";
        String MANUAL = "MANUAL";
    }

    /**
     * Code type constants for ResourceEntity.codeType
     */
    public interface CodeType {
        String DEFAULT = "default";
        String CUSTOM = "custom";
    }

    /**
     * Role type constants (string values for role_type dictionary)
     */
    public interface RoleTypeCode {
        String GROUP_ROLE = "GROUP_ROLE";
        String ROLE = "ROLE";
        String ORG = "ORG";
        String USER = "USER";
    }
}
