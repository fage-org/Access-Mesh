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

    /**
     * Operation constants for operation_permission.code
     */
    public interface Operation {
        String ACCESS = "ACCESS";
    }

    /**
     * Condition logic constants for permission condition evaluation
     */
    public interface ConditionLogic {
        String AND = "AND";
        String OR = "OR";
    }

    /**
     * Condition type constants for permission condition rules
     */
    public interface ConditionType {
        String DATE_RANGE = "DATE_RANGE";
        String TIME_RANGE = "TIME_RANGE";
        String IP_WHITELIST = "IP_WHITELIST";
        String IP_BLACKLIST = "IP_BLACKLIST";
    }
}
