package cn.ac.fage.accessmesh.access.permission.constant;

/**
 * AccessMesh 本地权限投影所有权与保留业务键。
 * <p>
 * 管理事实派生的 abstract_user / abstract_role / resource_entity / user_role
 * 统一标记 {@link #SERVICE_CODE}，只能由 {@code access.application} 经
 * LocalProjectionDomainService 写入。
 * </p>
 */
public final class LocalProjectionOwner {

    private LocalProjectionOwner() {}

    /** 本地投影 owner_service_code */
    public static final String SERVICE_CODE = "access-service";

    /** 旧内部同步来源，外部 sync 不得再冒充 */
    public static final String LEGACY_ADMIN_SOURCE = "admin-service";

    public static final String SUBJECT_ADMIN_USER = "ADMIN_USER";
    public static final String ROLE_ORG = "ORG";
    public static final String ROLE_POSITION = "POSITION";
    public static final String RESOURCE_ADMIN_USER = "ADMIN_USER";
    public static final String RESOURCE_ADMIN_ORG = "ADMIN_ORG";
    public static final String RESOURCE_ADMIN_MENU = "ADMIN_MENU";
    public static final String SOURCE_TYPE_SYS_USER_ORG = "SYS_USER_ORG";

    public static boolean isLocalOwner(String ownerServiceCode) {
        return SERVICE_CODE.equals(ownerServiceCode);
    }

    public static boolean isReservedSubjectType(String subjectTypeCode) {
        return SUBJECT_ADMIN_USER.equals(subjectTypeCode);
    }

    public static boolean isReservedRoleType(String roleTypeCode) {
        return ROLE_ORG.equals(roleTypeCode) || ROLE_POSITION.equals(roleTypeCode);
    }

    public static boolean isReservedResourceType(String resourceTypeCode) {
        return RESOURCE_ADMIN_USER.equals(resourceTypeCode)
            || RESOURCE_ADMIN_ORG.equals(resourceTypeCode)
            || RESOURCE_ADMIN_MENU.equals(resourceTypeCode);
    }

    public static boolean isReservedUserRoleSource(String sourceType) {
        return SOURCE_TYPE_SYS_USER_ORG.equals(sourceType);
    }

    public static boolean isInternalSourceService(String sourceService) {
        return SERVICE_CODE.equals(sourceService) || LEGACY_ADMIN_SOURCE.equals(sourceService);
    }
}
